-- =====================================================================
--  27) JEUX SOLO : le meilleur score de la JOURNÉE de chaque jeu gagne 10 F
--      À exécuter UNE fois dans Supabase > SQL Editor (relançable sans risque).
--
--  Règles :
--    * Jeux concernés (un seul joueur) : panda, mario, serpent, commando, liquide, casse, gta.
--      Les jeux à plusieurs (quiz, motard, foot, pes) gardent leur propre règle : aucun prix du jour.
--    * La journée va de minuit à minuit, heure du Bénin (Africa/Porto-Novo).
--    * Le prix est payé une fois la journée terminée : le meilleur score de la veille reçoit 10 points (1 point = 1 F)
--      ajoutés au solde « points gagnés » (table quiz_gains), retirable comme les gains du quiz.
--    * CONDITION : il faut avoir PROGRESSÉ. Seuls les joueurs dont le score du jour est strictement supérieur à leur
--      meilleur score d'avant ce jour (dans ce jeu) peuvent gagner. Si le meilleur de la journée n'a pas progressé
--      et que les autres sont plus bas, il ne reçoit rien (le prix va au meilleur des joueurs qui ont progressé, sinon à personne).
--      Les records déjà existants au moment de l'installation de cette section servent de point de départ.
--    * Égalité : le premier à avoir atteint ce score gagne. Score nul : pas de prix.
--    * Un seul prix par jeu et par jour (table prix_jeux_jour, clé jeu + jour).
-- =====================================================================

create table if not exists public.scores_jeux_jour (
    jeu         text        not null,
    jour        date        not null,
    utilisateur uuid        not null references auth.users (id) on delete cascade,
    nom         text,
    score       integer     not null default 0,
    maj         timestamptz not null default now(),
    primary key (jeu, jour, utilisateur),
    constraint score_jour_borne check (score between 0 and 1000000)
);
create index if not exists scores_jeux_jour_classement on public.scores_jeux_jour (jeu, jour, score desc, maj);

create table if not exists public.prix_jeux_jour (
    jeu         text        not null,
    jour        date        not null,
    utilisateur uuid        not null references auth.users (id) on delete cascade,
    nom         text,
    score       integer     not null,
    points      integer     not null,
    vu          boolean     not null default false,
    cree        timestamptz not null default now(),
    primary key (jeu, jour)
);

alter table public.scores_jeux_jour enable row level security;
alter table public.prix_jeux_jour  enable row level security;
revoke all on public.scores_jeux_jour from anon, authenticated;
revoke all on public.prix_jeux_jour  from anon, authenticated;
-- aucun accès direct : tout passe par les fonctions ci-dessous

-- Point de départ : les records actuels (jour fictif 1970-01-01, jamais payé) = « score d'avant » de chaque joueur.
insert into public.scores_jeux_jour (jeu, jour, utilisateur, nom, score, maj)
select jeu, date '1970-01-01', utilisateur, nom, score, maj
  from public.scores_jeux
 where jeu in ('panda', 'mario', 'serpent', 'commando', 'liquide', 'casse', 'gta') and score > 0
on conflict (jeu, jour, utilisateur) do nothing;

insert into public.quiz_regles (cle, valeur) values ('prix_jour_jeux', 10)
    on conflict (cle) do nothing;

-- Jour courant (heure du Bénin)
create or replace function public._jeux_jour() returns date
language sql stable as $$ select (now() at time zone 'Africa/Porto-Novo')::date $$;

-- Paie les journées terminées qui ne l'ont pas encore été.
create or replace function public._jeux_payer_jours() returns void
language plpgsql security definer set search_path = public as $$
declare
    r record;
    v_pts integer := coalesce((select valeur from public.quiz_regles where cle = 'prix_jour_jeux'), 10);
    v_n integer;
begin
    for r in
        select distinct on (s.jeu, s.jour) s.jeu, s.jour, s.utilisateur, s.nom, s.score
          from public.scores_jeux_jour s
         where s.jour < public._jeux_jour() and s.jour > date '1970-01-01' and s.score > 0
           -- a progressé : score du jour > meilleur score des jours précédents de ce joueur dans ce jeu
           and s.score > coalesce((select max(a.score) from public.scores_jeux_jour a
                                    where a.jeu = s.jeu and a.utilisateur = s.utilisateur and a.jour < s.jour), 0)
           and not exists (select 1 from public.prix_jeux_jour p where p.jeu = s.jeu and p.jour = s.jour)
         order by s.jeu, s.jour, s.score desc, s.maj asc
    loop
        insert into public.prix_jeux_jour (jeu, jour, utilisateur, nom, score, points)
        values (r.jeu, r.jour, r.utilisateur, r.nom, r.score, v_pts)
        on conflict (jeu, jour) do nothing;
        get diagnostics v_n = row_count;
        if v_n > 0 then
            insert into public.quiz_gains (user_id, points, victoires, maj)
            values (r.utilisateur, v_pts, 0, now())
            on conflict (user_id) do update
                set points = public.quiz_gains.points + excluded.points, maj = now();
        end if;
    end loop;
end $$;

-- Envoie le score du jour d'un joueur (jeux solo seulement). Appelée par l'appli quand un score est fait aujourd'hui.
create or replace function public.enregistrer_score_jour(p_jeu text, p_score integer) returns json
language plpgsql security definer set search_path = public as $$
declare
    v_uid   uuid := auth.uid();
    v_nom   text;
    v_score integer;
begin
    if v_uid is null then raise exception 'connexion requise'; end if;
    if p_jeu is null or p_jeu not in ('panda', 'mario', 'serpent', 'commando', 'liquide', 'casse', 'gta') then
        return json_build_object('ok', true, 'ignore', true);      -- jeu à plusieurs : pas de prix du jour
    end if;
    v_score := least(greatest(coalesce(p_score, 0), 0), 1000000);
    select nullif(btrim(nom), '') into v_nom from public.profils where id = v_uid;

    insert into public.scores_jeux_jour (jeu, jour, utilisateur, nom, score, maj)
    values (p_jeu, public._jeux_jour(), v_uid, coalesce(v_nom, 'Joueur'), v_score, now())
    on conflict (jeu, jour, utilisateur) do update
        set nom   = excluded.nom,
            score = greatest(public.scores_jeux_jour.score, excluded.score),
            maj   = case when excluded.score > public.scores_jeux_jour.score then now() else public.scores_jeux_jour.maj end;

    perform public._jeux_payer_jours();
    return json_build_object('ok', true);
end $$;

-- Paie les journées finies et rend mes nouveaux prix (une seule fois chacun) : {ok, prix:[{jeu, jour, points, score}]}
create or replace function public.jeux_payer_jours() returns json
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_res json;
begin
    if v_uid is null then raise exception 'connexion requise'; end if;
    perform public._jeux_payer_jours();
    with m as (
        update public.prix_jeux_jour set vu = true
         where utilisateur = v_uid and not vu
        returning jeu, jour, points, score)
    select coalesce(json_agg(json_build_object('jeu', jeu, 'jour', jour, 'points', points, 'score', score) order by jour, jeu), '[]'::json)
      into v_res from m;
    return json_build_object('ok', true, 'prix', v_res);
end $$;

-- Meilleur score d'aujourd'hui pour un jeu : {ok, nom, score}
create or replace function public.jeux_meilleur_du_jour(p_jeu text) returns json
language plpgsql security definer set search_path = public as $$
declare s record;
begin
    if auth.uid() is null then raise exception 'connexion requise'; end if;
    select nom, score into s from public.scores_jeux_jour
     where jeu = p_jeu and jour = public._jeux_jour() and score > 0
     order by score desc, maj asc limit 1;
    if not found then return json_build_object('ok', true, 'nom', '', 'score', 0); end if;
    return json_build_object('ok', true, 'nom', s.nom, 'score', s.score);
end $$;

revoke all on function public._jeux_jour() from public, anon, authenticated;
revoke all on function public._jeux_payer_jours() from public, anon, authenticated;
revoke all on function public.enregistrer_score_jour(text, integer) from public, anon, authenticated;
revoke all on function public.jeux_payer_jours() from public, anon, authenticated;
revoke all on function public.jeux_meilleur_du_jour(text) from public, anon, authenticated;
grant execute on function public.enregistrer_score_jour(text, integer) to authenticated;
grant execute on function public.jeux_payer_jours() to authenticated;
grant execute on function public.jeux_meilleur_du_jour(text) to authenticated;
