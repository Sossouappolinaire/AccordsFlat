-- =====================================================================
--  17) TV KOUAMÉ : PUBLICITÉ = BANDES D'ANNONCE DE TOUS LES FILMS ET SÉRIES DU JOUR, APRÈS CHAQUE FILM/SÉRIE
--     - après chaque film et chaque série, le programme contient un « spot publicitaire »
--     - le spot enchaîne la bande d'annonce de TOUS les films et séries programmés ce jour-là
--       (10 secondes chacune : c_bande_ms) ; il commence par le film qui vient ensuite dans le programme
--     - la bande d'annonce d'un film = un extrait qui commence par défaut à 40 % du film ; on peut choisir soi-même
--       le meilleur moment d'un film avec films_facebook.extrait_debut_ms (voir la fin du fichier)
--     - l'appli écrit et dit : « TV Kouamé est votre télévision Facebook. En Dieu nous comptons. »
--     - le spot fait partie du programme du serveur : tout le monde le voit en même temps
--     - un spot n'est mis que s'il tient avant minuit ; la journée reste entièrement couverte
--     - comme le spot occupe du temps, la journée contient un peu moins de films qu'avant (calcul automatique)
--  À exécuter UNE FOIS dans Supabase › SQL Editor, de préférence quand la nouvelle version de l'appli est publiée.
--  Prend effet à la prochaine fabrication du programme (00h00, ou « Refaire le programme d'aujourd'hui »).
--  Pour revenir en arrière : réexécuter section12_tv_programmation_variee.sql et rétablir tv_planning,
--  admin_tv_programme et admin_tv_generer d'origine (voir section8 et section12_sauvegarde_ancienne_fonction_tv.sql).
-- =====================================================================

alter table public.tv_programme add column if not exists pub    boolean not null default false;
alter table public.tv_programme add column if not exists pub_ms bigint  not null default 0;
alter table public.films_facebook add column if not exists extrait_debut_ms bigint;   -- meilleur moment (facultatif)

-- Simule la journée : combien de films/séries commencent avant minuit si chaque film/série est suivi d'un spot de p_r ms ?
create or replace function public._tv_simuler(p_dur bigint[], p_cat text[], p_ent bigint, p_r bigint) returns int
language plpgsql immutable as $$
declare t bigint := 0; endt bigint; st bigint; i int; nf int := 0;
begin
  for i in 1 .. coalesce(cardinality(p_dur), 0) loop
    if i = 1 and p_ent > 0 then st := -1; endt := p_ent;      -- film commencé la veille : il lui reste p_ent ms
    else st := t; endt := t + p_dur[i];
    end if;
    exit when st >= 86400000;
    if p_cat[i] in ('film', 'serie') then nf := nf + 1; end if;
    t := endt;
    if p_cat[i] in ('film', 'serie') and p_r > 0 and endt + p_r <= 86400000 then t := endt + p_r; end if;
  end loop;
  return nf;
end $$;
revoke all on function public._tv_simuler(bigint[], text[], bigint, bigint) from public, anon, authenticated;

create or replace function public._tv_fabriquer(p_jour date, p_canal text) returns integer
language plpgsql security definer set search_path = public as $$
declare
  c_bande_ms constant bigint := 10000;      -- durée de la bande d'annonce d'UN film/série (10 000 ou 20 000)
  e record; r record; rc record; rf record; rs record;
  cumul bigint := 0; n int := 0; garde int := 0;
  v_film uuid := null; v_reste bigint := 0; v_duree bigint := 0;
  s_film uuid := null; s_reste bigint := 0;
  etape int := 0; pf int := 0; ps int := 0; pc int := 0;
  d_etape int; d_film int; d_serie int; d_chanson int;
  cat text; pos int;
  deja uuid[] := '{}';
  derniere text := null;
  lourde text := null;
  quota int := 0;
  vues int := 0;
  doit_musique boolean;
  a_film uuid[] := '{}'; a_cat text[] := '{}'; a_dur bigint[] := '{}';
  a_pf int[] := '{}'; a_ps int[] := '{}'; a_pc int[] := '{}';
  nb_max int := 0; nb int; nf int; v_pub bigint := 0;
  i int; t bigint := 0; st bigint; endt bigint; dernier_film uuid := null;
  k_pf int; k_ps int; k_pc int;
begin
  select * into e from public.tv_etat where id = 1;
  if e.jour = p_jour - 1 then
    v_film := e.sortant_film;  v_reste := coalesce(e.sortant_ms, 0);
  elsif e.jour = p_jour then
    v_film := e.entrant_film;  v_reste := coalesce(e.entrant_ms, 0);
  end if;
  if e.jour = p_jour then
    etape := e.deb_etape; pf := e.deb_film; ps := e.deb_serie; pc := e.deb_chanson;
  elsif e.jour is not null and e.jour < p_jour then
    etape := e.fin_etape; pf := e.fin_film; ps := e.fin_serie; pc := e.fin_chanson;
  end if;
  d_etape := etape; d_film := pf; d_serie := ps; d_chanson := pc;
  k_pf := pf; k_ps := ps; k_pc := pc;

  -- ---------- passe 1 : la journée comme avant (sans spots), mise en mémoire
  if v_film is not null and v_reste > 0 then
    select duree_ms into v_duree from public.films_facebook where id = v_film and duree_ms > 0;
    if found and v_reste <= v_duree then
      cumul := v_reste;
      deja := array_append(deja, v_film);
      select f.categorie into derniere from public.films_facebook f where f.id = v_film;
      a_film := array_append(a_film, v_film); a_cat := array_append(a_cat, coalesce(derniere, 'chanson'));
      a_dur := array_append(a_dur, v_reste);
      a_pf := array_append(a_pf, pf); a_ps := array_append(a_ps, ps); a_pc := array_append(a_pc, pc);
      if derniere in ('film', 'serie') then
        lourde := derniere;
        quota := 1 + floor(random() * 5)::int;
      end if;
    else
      v_film := null; v_reste := 0;
    end if;
  else
    v_film := null; v_reste := 0;
  end if;
  if derniere is null or derniere not in ('film', 'serie') then
    derniere := 'chanson';
    quota := floor(random() * 3)::int;
  end if;
  while cumul < 86400000 and garde < 3000 loop
    garde := garde + 1;
    select * into rc from public._tv_prochain('chanson', pc, deja);
    select * into rf from public._tv_prochain('film',    pf, deja);
    select * into rs from public._tv_prochain('serie',   ps, deja);
    doit_musique := derniere in ('film', 'serie') or (derniere = 'chanson' and vues < quota);
    cat := null;
    if doit_musique and rc.film_id is not null then
      cat := 'chanson';
    elsif rf.film_id is not null and rs.film_id is not null then
      cat := case when lourde = 'film' then 'serie'
                  when lourde = 'serie' then 'film'
                  when random() < 0.5 then 'film' else 'serie' end;
    elsif rf.film_id is not null then
      cat := 'film';
    elsif rs.film_id is not null then
      cat := 'serie';
    elsif rc.film_id is not null then
      cat := 'chanson';
    end if;
    exit when cat is null;
    if cat = 'chanson' then r := rc; pos := pc;
    elsif cat = 'film' then r := rf; pos := pf;
    else r := rs; pos := ps;
    end if;
    cumul := cumul + r.duree;
    if cat = 'film' then pf := r.nouvelle_pos;
    elsif cat = 'serie' then ps := r.nouvelle_pos;
    else pc := r.nouvelle_pos;
    end if;
    a_film := array_append(a_film, r.film_id); a_cat := array_append(a_cat, cat); a_dur := array_append(a_dur, r.duree);
    a_pf := array_append(a_pf, pf); a_ps := array_append(a_ps, ps); a_pc := array_append(a_pc, pc);
    if cat = 'chanson' then
      vues := vues + 1;
    else
      deja := array_append(deja, r.film_id);
      lourde := cat;
      quota := 1 + floor(random() * 5)::int;
      vues := 0;
    end if;
    derniere := cat;
  end loop;

  -- ---------- passe 2 : durée du spot = bandes d'annonce de tous les films/séries qui passeront vraiment aujourd'hui
  select count(*) into nb_max from unnest(a_cat) c where c in ('film', 'serie');
  nb := nb_max;
  while nb > 0 loop
    nf := public._tv_simuler(a_dur, a_cat, v_reste, nb * c_bande_ms);
    exit when nf >= nb;
    nb := nb - 1;
  end loop;
  v_pub := nb * c_bande_ms;

  -- ---------- passe 3 : écriture du programme avec un spot après chaque film/série
  delete from public.tv_programme where true;
  for i in 1 .. coalesce(cardinality(a_film), 0) loop
    if i = 1 and v_film is not null then st := v_reste - v_duree; endt := v_reste;
    else st := t; endt := t + a_dur[i];
    end if;
    exit when st >= 86400000;
    n := n + 1;
    insert into public.tv_programme (ordre, film_id, canal, debut_ms) values (n, a_film[i], p_canal, st);
    dernier_film := a_film[i];
    k_pf := a_pf[i]; k_ps := a_ps[i]; k_pc := a_pc[i];
    t := endt;
    if a_cat[i] in ('film', 'serie') and v_pub > 0 and endt + v_pub <= 86400000 then
      n := n + 1;
      -- le spot porte (provisoirement) le film qui le précède ; l'appli lit la liste des films/séries du jour
      insert into public.tv_programme (ordre, film_id, canal, debut_ms, pub, pub_ms) values (n, a_film[i], p_canal, endt, true, v_pub);
      t := endt + v_pub;
    end if;
  end loop;
  cumul := t;
  pf := k_pf; ps := k_ps; pc := k_pc;      -- les films coupés par les spots seront repris plus tard

  if cumul > 86400000 then
    s_film := dernier_film;
    s_reste := cumul - 86400000;
  end if;
  update public.tv_etat
     set jour = p_jour, canal = p_canal,
         entrant_film = v_film, entrant_ms = v_reste,
         sortant_film = s_film, sortant_ms = s_reste,
         deb_etape = d_etape, deb_film = d_film, deb_serie = d_serie, deb_chanson = d_chanson,
         fin_etape = etape, fin_film = pf, fin_serie = ps, fin_chanson = pc
   where id = 1;
  return n;
end $$;
revoke all on function public._tv_fabriquer(date, text) from public, anon, authenticated;

-- Lu par l'appli. « pub » = spot (sa durée est celle du spot) ; « extrait_ms » = début de la bande d'annonce du film.
create or replace function public.tv_planning() returns json
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'connexion requise'; end if;
  perform public._tv_si_besoin();
  return json_build_object(
    'jour', (now() at time zone 'Africa/Porto-Novo')::date,
    'maintenant_ms', (extract(epoch from (now() at time zone 'Africa/Porto-Novo')::time) * 1000)::bigint,
    'programmes', coalesce((
      select json_agg(json_build_object('ordre', t.ordre, 'canal', t.canal, 'titre', f.titre,
                                        'duree_ms', case when t.pub then t.pub_ms else f.duree_ms end,
                                        'debut_ms', t.debut_ms, 'lien', f.lien, 'serie', f.serie, 'episode', f.episode,
                                        'categorie', f.categorie,
                                        'pub', t.pub,
                                        'extrait_ms', greatest(0, least(coalesce(f.extrait_debut_ms, (f.duree_ms * 0.4)::bigint),
                                                                        f.duree_ms - 21000)))
                      order by t.ordre)
        from public.tv_programme t join public.films_facebook f on f.id = t.film_id
       where f.duree_ms > 0), '[]'::json));
end $$;
revoke all on function public.tv_planning() from public, anon, authenticated;
grant execute on function public.tv_planning() to authenticated;

-- Écran administrateur « programme du jour » : sans les spots (ce ne sont pas des films en plus).
create or replace function public.admin_tv_programme() returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.est_admin() then raise exception 'reserve a l administrateur'; end if;
  return json_build_object(
    'jour', (select jour from public.tv_etat where id = 1),
    'programmes', coalesce((
      select json_agg(json_build_object('ordre', t.ordre, 'id', f.id, 'titre', f.titre, 'categorie', f.categorie,
                                        'serie', f.serie, 'episode', f.episode, 'duree_ms', f.duree_ms,
                                        'debut_ms', t.debut_ms, 'exclu', f.tv_exclu) order by t.ordre)
        from public.tv_programme t join public.films_facebook f on f.id = t.film_id
       where not t.pub), '[]'::json));
end $$;
revoke all on function public.admin_tv_programme() from public, anon, authenticated;
grant execute on function public.admin_tv_programme() to authenticated;

-- « Refaire le programme d'aujourd'hui » : la durée totale annoncée ne compte pas les spots.
create or replace function public.admin_tv_generer(p_canal text default 'Kouamé TV') returns json
language plpgsql security definer set search_path = public as $$
declare n int; total bigint; v_canal text := left(coalesce(nullif(trim(p_canal), ''), 'Kouamé TV'), 60);
begin
  if not public.est_admin() then raise exception 'reserve a l administrateur'; end if;
  perform pg_advisory_xact_lock(8675309);
  n := public._tv_fabriquer((now() at time zone 'Africa/Porto-Novo')::date, v_canal);
  select coalesce(sum(f.duree_ms), 0) into total
    from public.tv_programme t join public.films_facebook f on f.id = t.film_id
   where not t.pub;
  return json_build_object('ok', true, 'nombre', n, 'duree_ms', total);
end $$;
revoke all on function public.admin_tv_generer(text) from public, anon, authenticated;
grant execute on function public.admin_tv_generer(text) to authenticated;

-- ---------------------------------------------------------------------
-- FACULTATIF : choisir soi-même le meilleur moment d'un film pour sa bande d'annonce (millisecondes depuis le début).
-- Exemple : le film dont le titre contient « Mot » passe sa bande d'annonce à partir de 1 h 02 min 30 s :
--   update public.films_facebook set extrait_debut_ms = (1*3600 + 2*60 + 30) * 1000 where titre ilike '%Mot%';
-- Pour revenir au moment automatique (40 % du film) :
--   update public.films_facebook set extrait_debut_ms = null where titre ilike '%Mot%';
-- ---------------------------------------------------------------------
