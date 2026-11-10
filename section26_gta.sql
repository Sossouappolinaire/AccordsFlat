-- =====================================================================
--  26) JEUX : classement de « GTA Kouamé » (clé « gta »)
--      À exécuter UNE fois dans Supabase > SQL Editor (relançable sans risque).
--      Autorise la clé de jeu « gta » dans la table scores_jeux et dans la fonction enregistrer_score().
-- =====================================================================
alter table public.scores_jeux drop constraint if exists jeu_connu;
alter table public.scores_jeux add constraint jeu_connu
    check (jeu in ('panda', 'mario', 'serpent', 'commando', 'liquide', 'motard', 'foot', 'pes', 'quiz', 'casse', 'gta'));

create or replace function public.enregistrer_score(p_jeu text, p_score integer) returns json
language plpgsql security definer set search_path = public as $$
declare
    v_uid   uuid := auth.uid();
    v_nom   text;
    v_score integer;
begin
    if v_uid is null then raise exception 'connexion requise'; end if;
    if p_jeu is null or p_jeu not in ('panda', 'mario', 'serpent', 'commando', 'liquide', 'motard', 'foot', 'pes', 'quiz', 'casse', 'gta') then
        raise exception 'jeu inconnu';
    end if;
    v_score := least(greatest(coalesce(p_score, 0), 0), 1000000);
    select nullif(btrim(nom), '') into v_nom from public.profils where id = v_uid;

    insert into public.scores_jeux (utilisateur, jeu, nom, score, maj)
    values (v_uid, p_jeu, coalesce(v_nom, 'Joueur'), v_score, now())
    on conflict (utilisateur, jeu) do update
        set nom   = excluded.nom,
            score = greatest(public.scores_jeux.score, excluded.score),
            maj   = case when excluded.score > public.scores_jeux.score then now() else public.scores_jeux.maj end;

    return json_build_object('ok', true);
end $$;

revoke all on function public.enregistrer_score(text, integer) from public, anon, authenticated;
grant execute on function public.enregistrer_score(text, integer) to authenticated;
