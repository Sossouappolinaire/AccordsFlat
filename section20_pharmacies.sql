-- =====================================================================
--  20) PHARMACIES DE GARDE DU BÉNIN
--     - table des pharmacies : département, commune, ville/village (localité), nom, adresse, téléphone
--     - une pharmacie est « de garde » quand la date du jour (heure du Bénin) est entre garde_debut et garde_fin
--       (le tour de garde officiel va du lundi au dimanche)
--     - tout utilisateur connecté LIT la liste ; seul l'administrateur ajoute, modifie, supprime
--       et fixe la garde (bouton « De garde cette semaine »)
--  À exécuter UNE FOIS dans Supabase › SQL Editor.
-- =====================================================================

create table if not exists public.pharmacies (
    id           uuid primary key default gen_random_uuid(),
    departement  text not null,
    commune      text not null,
    localite     text not null default '',
    nom          text not null,
    adresse      text not null default '',
    telephone    text not null default '',
    garde_debut  date,
    garde_fin    date,
    cree_le      timestamptz not null default now(),
    modifie_le   timestamptz not null default now()
);

create index if not exists pharmacies_lieu_idx on public.pharmacies (departement, commune);

alter table public.pharmacies enable row level security;
revoke all on public.pharmacies from anon, authenticated;

-- Liste complète (utilisateur connecté).
create or replace function public.pharmacies_liste() returns json
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'connexion requise'; end if;
  return coalesce((
    select json_agg(json_build_object(
             'id', p.id, 'departement', p.departement, 'commune', p.commune, 'localite', p.localite,
             'nom', p.nom, 'adresse', p.adresse, 'telephone', p.telephone,
             'garde_debut', p.garde_debut, 'garde_fin', p.garde_fin)
           order by p.departement, p.commune, p.localite, p.nom)
      from public.pharmacies p), '[]'::json);
end $$;

-- Ajouter (p_id null) ou modifier (p_id renseigné) une pharmacie. Administrateur seulement.
create or replace function public.pharmacie_enregistrer(
  p_id uuid, p_departement text, p_commune text, p_localite text,
  p_nom text, p_adresse text, p_telephone text) returns json
language plpgsql security definer set search_path = public as $$
declare
  v_dep  text := trim(coalesce(p_departement, ''));
  v_com  text := trim(coalesce(p_commune, ''));
  v_loc  text := trim(coalesce(p_localite, ''));
  v_nom  text := trim(coalesce(p_nom, ''));
  v_adr  text := trim(coalesce(p_adresse, ''));
  v_tel  text := trim(coalesce(p_telephone, ''));
begin
  if not public.est_admin() then
    return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
  end if;
  if v_dep not in ('Alibori','Atacora','Atlantique','Borgou','Collines','Couffo',
                   'Donga','Littoral','Mono','Ouémé','Plateau','Zou') then
    return json_build_object('ok', false, 'erreur', 'Département inconnu.');
  end if;
  if v_com = '' or length(v_com) > 80 or length(v_loc) > 80 or length(v_adr) > 200 or length(v_tel) > 40 then
    return json_build_object('ok', false, 'erreur', 'Commune, localité, adresse ou téléphone invalide.');
  end if;
  if v_nom = '' or length(v_nom) > 120 then
    return json_build_object('ok', false, 'erreur', 'Entrez le nom de la pharmacie.');
  end if;

  if p_id is null then
    insert into public.pharmacies (departement, commune, localite, nom, adresse, telephone)
    values (v_dep, v_com, v_loc, v_nom, v_adr, v_tel);
  else
    update public.pharmacies
       set departement = v_dep, commune = v_com, localite = v_loc, nom = v_nom,
           adresse = v_adr, telephone = v_tel, modifie_le = now()
     where id = p_id;
    if not found then return json_build_object('ok', false, 'erreur', 'Pharmacie introuvable.'); end if;
  end if;
  return json_build_object('ok', true);
end $$;

-- Fixer la garde (dates incluses) ; p_debut et p_fin à null = plus de garde. Administrateur seulement.
create or replace function public.pharmacie_garde(p_id uuid, p_debut date, p_fin date) returns json
language plpgsql security definer set search_path = public as $$
begin
  if not public.est_admin() then
    return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
  end if;
  if (p_debut is null) <> (p_fin is null) or (p_debut is not null and p_fin < p_debut) then
    return json_build_object('ok', false, 'erreur', 'Dates de garde invalides.');
  end if;
  update public.pharmacies set garde_debut = p_debut, garde_fin = p_fin, modifie_le = now() where id = p_id;
  if not found then return json_build_object('ok', false, 'erreur', 'Pharmacie introuvable.'); end if;
  return json_build_object('ok', true);
end $$;

create or replace function public.pharmacie_supprimer(p_id uuid) returns json
language plpgsql security definer set search_path = public as $$
begin
  if not public.est_admin() then
    return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
  end if;
  delete from public.pharmacies where id = p_id;
  if not found then return json_build_object('ok', false, 'erreur', 'Pharmacie introuvable.'); end if;
  return json_build_object('ok', true);
end $$;

revoke all on function public.pharmacies_liste() from public, anon, authenticated;
revoke all on function public.pharmacie_enregistrer(uuid, text, text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.pharmacie_garde(uuid, date, date) from public, anon, authenticated;
revoke all on function public.pharmacie_supprimer(uuid) from public, anon, authenticated;

grant execute on function public.pharmacies_liste() to authenticated;
grant execute on function public.pharmacie_enregistrer(uuid, text, text, text, text, text, text) to authenticated;
grant execute on function public.pharmacie_garde(uuid, date, date) to authenticated;
grant execute on function public.pharmacie_supprimer(uuid) to authenticated;
