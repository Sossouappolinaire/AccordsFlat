-- =====================================================================
--  23) PURGE AUTOMATIQUE DES ANCIENNES VERSIONS + TÉLÉCHARGEMENTS VISIBLES PAR L'ADMINISTRATEUR — relançable
--      À exécuter après section10_mise_a_jour_apk.sql et supabase_setup.sql.
--
--      A) Quand l'administrateur publie une version, toutes les versions plus anciennes sont supprimées de la
--         table maj_apk (leurs notifications partent avec, en cascade). La fonction renvoie aussi la liste des
--         anciens fichiers APK du dossier « apk-app » : l'appli de l'administrateur les supprime via l'API de
--         stockage (supprimer des lignes de storage.objects en SQL ne supprime pas le fichier lui-même).
--      B) admin_telechargements() : qui a téléchargé quel film / quelle série, quand, avec le classement des
--         films les plus téléchargés et le total par utilisateur (administrateur seulement).
-- =====================================================================

-- A) Publication : on ne garde que la dernière version.
create or replace function public.admin_maj_publier(
    p_version_code integer, p_version_name text, p_note text, p_apk text, p_taille bigint, p_obligatoire boolean default false)
returns json language plpgsql security definer set search_path = public, storage as $$
declare v_max integer;
begin
    if not public.est_admin() then raise exception 'reserve a l administrateur'; end if;
    if p_version_code is null or p_version_code < 1 or coalesce(trim(p_version_name), '') = ''
       or coalesce(trim(p_apk), '') = '' then
        return json_build_object('ok', false, 'erreur', 'donnees_invalides');
    end if;
    if not exists (select 1 from storage.objects where bucket_id = 'apk-app' and name = p_apk) then
        return json_build_object('ok', false, 'erreur', 'apk_introuvable');
    end if;
    select coalesce(max(version_code), 0) into v_max from public.maj_apk;
    if p_version_code <= v_max then
        return json_build_object('ok', false, 'erreur', 'version_trop_ancienne', 'derniere', v_max);
    end if;
    insert into public.maj_apk (version_code, version_name, note, apk, taille, obligatoire)
    values (p_version_code, left(trim(p_version_name), 40), left(nullif(trim(coalesce(p_note, '')), ''), 1000),
            p_apk, p_taille, coalesce(p_obligatoire, false));
    -- Les anciennes versions disparaissent de la base.
    delete from public.maj_apk where version_code < p_version_code;
    -- Les anciens fichiers APK (tout sauf le nouveau) : à supprimer par l'appli via l'API de stockage.
    return json_build_object('ok', true,
        'anciens', coalesce((select json_agg(name) from storage.objects where bucket_id = 'apk-app' and name <> p_apk), '[]'::json));
end $$;

revoke all on function public.admin_maj_publier(integer, text, text, text, bigint, boolean) from public, anon, authenticated;
grant execute on function public.admin_maj_publier(integer, text, text, text, bigint, boolean) to authenticated;

-- B) Téléchargements de films / séries : tout est visible par l'administrateur.
create or replace function public.admin_telechargements(p_limite integer default 300)
returns json language plpgsql stable security definer set search_path = public as $$
declare v_lim integer := least(greatest(coalesce(p_limite, 300), 1), 1000);
begin
    if not public.est_admin() then raise exception 'reserve a l administrateur'; end if;
    return json_build_object(
        'ok', true,
        'total', (select count(*) from public.telechargements),
        'utilisateurs', (select count(distinct utilisateur) from public.telechargements),
        'liste', coalesce((
            select json_agg(json_build_object(
                       'nom', coalesce(p.nom, 'Compte inconnu'), 'email', coalesce(p.email, ''),
                       'titre', f.titre, 'serie', f.serie, 'episode', f.episode, 'lien', t.lien,
                       'cree_ms', (extract(epoch from t.cree_le) * 1000)::bigint) order by t.cree_le desc)
            from (select * from public.telechargements order by cree_le desc limit v_lim) t
            left join public.profils p on p.id = t.utilisateur
            left join lateral (select titre, serie, episode from public.films_facebook where lien = t.lien limit 1) f on true
        ), '[]'::json),
        'top', coalesce((
            select json_agg(row_to_json(r) order by r.nb desc, r.titre)
            from (select coalesce(f.titre, t.lien, 'Lien inconnu') as titre, f.serie, f.episode,
                         count(*) as nb, count(distinct t.utilisateur) as personnes
                  from public.telechargements t
                  left join lateral (select titre, serie, episode from public.films_facebook where lien = t.lien limit 1) f on true
                  group by 1, 2, 3 order by nb desc, titre limit 20) r
        ), '[]'::json),
        'par_utilisateur', coalesce((
            select json_agg(row_to_json(u) order by u.nb desc, u.nom)
            from (select coalesce(p.nom, 'Compte inconnu') as nom, coalesce(p.email, '') as email,
                         count(*) as nb, (extract(epoch from max(t.cree_le)) * 1000)::bigint as dernier_ms
                  from public.telechargements t
                  left join public.profils p on p.id = t.utilisateur
                  group by t.utilisateur, p.nom, p.email order by nb desc, nom limit 200) u
        ), '[]'::json)
    );
end $$;

revoke all on function public.admin_telechargements(integer) from public, anon, authenticated;
grant execute on function public.admin_telechargements(integer) to authenticated;
