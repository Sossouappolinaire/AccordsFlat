-- =====================================================================
--  21) IMPORT EN LOT DES PHARMACIES (fichier Excel .xlsx ou CSV chargé par l'appli)
--     - l'administrateur choisit un fichier dans « Pharmacies de garde » ; l'appli le lit et envoie les lignes ici
--     - une pharmacie déjà enregistrée (même département, commune, ville/village et nom, sans tenir compte des
--       majuscules) est MISE À JOUR ; sinon elle est AJOUTÉE
--     - garde_debut et garde_fin (facultatives) fixent la garde ; si elles sont vides, la garde déjà enregistrée est conservée
--     - une ligne invalide est refusée seule : les autres lignes du lot sont quand même enregistrées
--  À exécuter UNE FOIS dans Supabase › SQL Editor (après section20_pharmacies.sql).
-- =====================================================================

create or replace function public.pharmacies_importer(p_lignes jsonb) returns json
language plpgsql security definer set search_path = public as $$
declare
  e      jsonb;
  v_dep  text; v_com text; v_loc text; v_nom text; v_adr text; v_tel text;
  v_deb  date; v_fin date; v_id uuid;
  i      int := 0; n_aj int := 0; n_mod int := 0; n_err int := 0;
  detail jsonb := '[]'::jsonb;
  v_ligne text;
begin
  if not public.est_admin() then
    return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
  end if;
  if p_lignes is null or jsonb_typeof(p_lignes) <> 'array' then
    return json_build_object('ok', false, 'erreur', 'Liste de pharmacies invalide.');
  end if;
  if jsonb_array_length(p_lignes) > 500 then
    return json_build_object('ok', false, 'erreur', 'Trop de lignes à la fois (500 au maximum).');
  end if;

  for e in select value from jsonb_array_elements(p_lignes) loop
    i := i + 1;
    v_ligne := coalesce(nullif(e->>'ligne', ''), i::text);
    begin
      v_dep := trim(coalesce(e->>'departement', ''));
      v_com := trim(coalesce(e->>'commune', ''));
      v_loc := trim(coalesce(e->>'localite', ''));
      v_nom := trim(coalesce(e->>'nom', ''));
      v_adr := trim(coalesce(e->>'adresse', ''));
      v_tel := trim(coalesce(e->>'telephone', ''));
      v_deb := nullif(trim(coalesce(e->>'garde_debut', '')), '')::date;
      v_fin := nullif(trim(coalesce(e->>'garde_fin', '')), '')::date;

      if v_dep not in ('Alibori','Atacora','Atlantique','Borgou','Collines','Couffo',
                       'Donga','Littoral','Mono','Ouémé','Plateau','Zou') then
        raise exception 'département inconnu « % »', v_dep;
      end if;
      if v_com = '' or length(v_com) > 80 or length(v_loc) > 80 or length(v_adr) > 200 or length(v_tel) > 40 then
        raise exception 'commune, localité, adresse ou téléphone invalide';
      end if;
      if v_nom = '' or length(v_nom) > 120 then
        raise exception 'nom de la pharmacie manquant ou trop long';
      end if;
      if (v_deb is null) <> (v_fin is null) or (v_deb is not null and v_fin < v_deb) then
        raise exception 'dates de garde invalides';
      end if;

      select id into v_id from public.pharmacies
       where departement = v_dep and lower(commune) = lower(v_com)
         and lower(localite) = lower(v_loc) and lower(nom) = lower(v_nom)
       limit 1;

      if v_id is null then
        insert into public.pharmacies (departement, commune, localite, nom, adresse, telephone, garde_debut, garde_fin)
        values (v_dep, v_com, v_loc, v_nom, v_adr, v_tel, v_deb, v_fin);
        n_aj := n_aj + 1;
      else
        update public.pharmacies
           set adresse     = case when v_adr = '' then adresse   else v_adr end,
               telephone   = case when v_tel = '' then telephone else v_tel end,
               garde_debut = coalesce(v_deb, garde_debut),
               garde_fin   = coalesce(v_fin, garde_fin),
               modifie_le  = now()
         where id = v_id;
        n_mod := n_mod + 1;
      end if;
    exception when others then
      n_err := n_err + 1;
      if n_err <= 20 then
        detail := detail || to_jsonb('Ligne ' || v_ligne || ' : ' || sqlerrm);
      end if;
    end;
  end loop;

  return json_build_object('ok', true, 'ajoutees', n_aj, 'modifiees', n_mod, 'refusees', n_err, 'detail', detail);
end $$;

revoke all on function public.pharmacies_importer(jsonb) from public, anon, authenticated;
grant execute on function public.pharmacies_importer(jsonb) to authenticated;
