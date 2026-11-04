-- =====================================================================
-- SECTION 19 : SECRÉTARIAT (comptes secrétaire + listes des producteurs)
-- À exécuter une seule fois dans Supabase (SQL Editor), APRÈS section18.
--
--  * L'administrateur crée un compte secrétaire : nom et prénom + numéro +
--    PIN + le massier dont il est le secrétaire.
--  * Le secrétaire prépare en brouillon (dans l'appli) une liste :
--    nom du producteur, nombre de 5/15, nombre de 5/10. Quand il valide,
--    toute la liste est envoyée à l'administrateur. Il voit le stock de
--    son massier.
--  * L'administrateur lit le secrétariat (prix de production de chaque
--    producteur) et peut modifier, ajouter et supprimer des lignes.
--  * Une liste = un « lot ». Après 2 jours, l'appli la range dans
--    « Voir les anciens points ».
-- =====================================================================

create table if not exists public.stock_secretaires (
    telephone text primary key,
    nom       text not null,
    pin       text not null,
    massier   text not null,
    cree_le   timestamptz not null default now(),
    cree_par  text
);

create table if not exists public.stock_secretariat (
    id             bigserial primary key,
    lot            bigint not null,
    massier        text not null,
    producteur     text not null,
    n15            integer not null default 0 check (n15 >= 0),
    n10            integer not null default 0 check (n10 >= 0),
    auteur_role    text not null check (auteur_role in ('SECRETARY', 'ADMIN')),
    secretaire_tel text,
    secretaire_nom text not null,
    cree_le        timestamptz not null default now(),
    modifie_le     timestamptz
);

alter table public.stock_secretaires enable row level security;
alter table public.stock_secretariat enable row level security;
revoke all on public.stock_secretaires from anon, authenticated;
revoke all on public.stock_secretariat from anon, authenticated;
revoke all on sequence public.stock_secretariat_id_seq from anon, authenticated;

-- ---------------------------------------------------------------------
-- Qui appelle ? Administrateur, propriétaire ou secrétaire (numéro + PIN).
-- ---------------------------------------------------------------------
create or replace function public._stock_qui(p_tel text, p_pin text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    v_nom text;
    v_massier text;
    v_essais record;
    v_existe boolean;
begin
    if public.est_admin() then
        select nom into v_nom from public.profils where id = auth.uid();
        return json_build_object('ok', true, 'role', 'ADMIN', 'tel', '',
                                 'nom', coalesce(nullif(trim(v_nom), ''), 'Administrateur'));
    end if;

    p_tel := regexp_replace(coalesce(p_tel, ''), '\s', '', 'g');
    v_existe := exists (select 1 from public.stock_proprietaires where telephone = p_tel)
             or exists (select 1 from public.stock_secretaires   where telephone = p_tel);

    if v_existe then
        select * into v_essais from public.stock_essais where telephone = p_tel;
        if found then
            if v_essais.echecs >= 5 and v_essais.dernier > now() - interval '15 minutes' then
                return json_build_object('ok', false, 'erreur', 'Trop d''essais. Réessayez dans 15 minutes.');
            end if;
        end if;

        select nom into v_nom from public.stock_proprietaires
         where telephone = p_tel and pin = coalesce(p_pin, '');
        if found then
            delete from public.stock_essais where telephone = p_tel;
            return json_build_object('ok', true, 'role', 'OWNER', 'tel', p_tel, 'nom', v_nom);
        end if;

        select nom, massier into v_nom, v_massier from public.stock_secretaires
         where telephone = p_tel and pin = coalesce(p_pin, '');
        if found then
            delete from public.stock_essais where telephone = p_tel;
            return json_build_object('ok', true, 'role', 'SECRETARY', 'tel', p_tel,
                                     'nom', v_nom, 'massier', v_massier);
        end if;

        insert into public.stock_essais (telephone, echecs, dernier) values (p_tel, 1, now())
        on conflict (telephone) do update
           set echecs = case when public.stock_essais.dernier > now() - interval '15 minutes'
                             then public.stock_essais.echecs + 1 else 1 end,
               dernier = now();
    end if;

    return json_build_object('ok', false, 'erreur', 'Numéro ou PIN incorrect.');
end;
$$;

-- ---------------------------------------------------------------------
-- Toutes les données. Le secrétaire ne reçoit que son massier et ses listes.
-- ---------------------------------------------------------------------
create or replace function public.stock_donnees(p_tel text, p_pin text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
    v_role text;
    v_massier text;
    v_tel text;
begin
    q := public._stock_qui(p_tel, p_pin);
    if not (q ->> 'ok')::boolean then return q; end if;
    v_role := q ->> 'role';
    v_massier := q ->> 'massier';
    v_tel := q ->> 'tel';

    return json_build_object(
        'ok', true,
        'role', v_role,
        'nom', q ->> 'nom',
        'massier', coalesce(v_massier, ''),
        'entrees', coalesce((
            select json_agg(json_build_object(
                       'type', o.type, 'massier', o.massier, 'n15', o.n15, 'n10', o.n10,
                       'ts', to_char(o.cree_le at time zone 'Africa/Porto-Novo', 'DD/MM/YYYY HH24:MI:SS'),
                       'par', o.auteur_nom, 'role', o.auteur_role)
                   order by o.id desc)
              from public.stock_operations o
             where v_role <> 'SECRETARY' or o.massier = v_massier), '[]'::json),
        'proprietaires', case when v_role = 'ADMIN' then coalesce((
            select json_agg(json_build_object(
                       'tel', p.telephone, 'nom', p.nom,
                       'cree', to_char(p.cree_le at time zone 'Africa/Porto-Novo', 'DD/MM/YYYY HH24:MI:SS'))
                   order by p.cree_le desc)
              from public.stock_proprietaires p), '[]'::json)
            else '[]'::json end,
        'secretaires', case when v_role = 'ADMIN' then coalesce((
            select json_agg(json_build_object(
                       'tel', s.telephone, 'nom', s.nom, 'massier', s.massier,
                       'cree', to_char(s.cree_le at time zone 'Africa/Porto-Novo', 'DD/MM/YYYY HH24:MI:SS'))
                   order by s.cree_le desc)
              from public.stock_secretaires s), '[]'::json)
            else '[]'::json end,
        'secretariat', case when v_role in ('ADMIN', 'SECRETARY') then coalesce((
            select json_agg(json_build_object(
                       'id', t.id, 'lot', t.lot, 'massier', t.massier, 'producteur', t.producteur,
                       'n15', t.n15, 'n10', t.n10,
                       'ts', to_char(t.cree_le at time zone 'Africa/Porto-Novo', 'DD/MM/YYYY HH24:MI:SS'),
                       'par', t.secretaire_nom, 'role', t.auteur_role)
                   order by t.id desc)
              from public.stock_secretariat t
             where v_role = 'ADMIN' or t.secretaire_tel = v_tel), '[]'::json)
            else '[]'::json end
    );
end;
$$;

-- ---------------------------------------------------------------------
-- Créer un compte secrétaire (administrateur seulement), lié à un massier.
-- ---------------------------------------------------------------------
create or replace function public.stock_creer_secretaire(
    p_nom text, p_numero text, p_code text, p_massier text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
    v_nom text := trim(coalesce(p_nom, ''));
    v_num text := regexp_replace(coalesce(p_numero, ''), '\s', '', 'g');
    v_massier text := trim(coalesce(p_massier, ''));
    v_exist text;
begin
    q := public._stock_qui(null, null);
    if not (q ->> 'ok')::boolean or (q ->> 'role') <> 'ADMIN' then
        return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
    end if;

    if length(v_nom) < 2 or length(v_nom) > 80 then
        return json_build_object('ok', false, 'erreur', 'Entrez le nom et prénom du secrétaire.');
    end if;
    if v_num !~ '^\+?[0-9]{8,15}$' then
        return json_build_object('ok', false, 'erreur', 'Numéro de téléphone invalide.');
    end if;
    if coalesce(p_code, '') !~ '^[0-9]{4}$' then
        return json_build_object('ok', false, 'erreur', 'Le PIN doit avoir 4 chiffres.');
    end if;
    if v_massier = '' or length(v_massier) > 80 then
        return json_build_object('ok', false, 'erreur', 'Choisissez le massier du secrétaire.');
    end if;
    if exists (select 1 from public.stock_proprietaires where telephone = v_num)
       or exists (select 1 from public.stock_secretaires where telephone = v_num) then
        return json_build_object('ok', false, 'erreur', 'Ce numéro existe déjà.');
    end if;

    -- même orthographe que le massier déjà enregistré en production
    select massier into v_exist from public.stock_operations
     where lower(massier) = lower(v_massier) limit 1;
    if found then v_massier := v_exist; end if;

    insert into public.stock_secretaires (telephone, nom, pin, massier, cree_par)
    values (v_num, v_nom, p_code, v_massier, q ->> 'nom');
    return json_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------
-- Le secrétaire envoie sa liste complète (un lot) : [{producteur, n15, n10}, ...]
-- ---------------------------------------------------------------------
create or replace function public.stock_secretariat_envoyer(p_tel text, p_pin text, p_lignes json)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
    v_lot bigint;
    v_nb integer;
    l json;
    v_prod text;
    v_a integer;
    v_b integer;
    i integer := 0;
begin
    q := public._stock_qui(p_tel, p_pin);
    if not (q ->> 'ok')::boolean then return q; end if;
    if (q ->> 'role') <> 'SECRETARY' then
        return json_build_object('ok', false, 'erreur', 'Réservé au secrétaire.');
    end if;

    if p_lignes is null or json_typeof(p_lignes) <> 'array' then
        return json_build_object('ok', false, 'erreur', 'Liste invalide.');
    end if;
    v_nb := json_array_length(p_lignes);
    if v_nb = 0 or v_nb > 300 then
        return json_build_object('ok', false, 'erreur', 'La liste doit avoir entre 1 et 300 lignes.');
    end if;

    -- tout vérifier avant d'enregistrer quoi que ce soit
    for l in select * from json_array_elements(p_lignes) loop
        i := i + 1;
        v_prod := trim(coalesce(l ->> 'producteur', ''));
        v_a := coalesce((l ->> 'n15')::integer, 0);
        v_b := coalesce((l ->> 'n10')::integer, 0);
        if v_prod = '' or length(v_prod) > 80 then
            return json_build_object('ok', false, 'erreur', 'Ligne ' || i || ' : nom invalide.');
        end if;
        if v_a < 0 or v_b < 0 or v_a > 1000000 or v_b > 1000000 or (v_a = 0 and v_b = 0) then
            return json_build_object('ok', false, 'erreur', 'Ligne ' || i || ' : quantité invalide.');
        end if;
    end loop;

    perform pg_advisory_xact_lock(18002);
    select coalesce(max(lot), 0) + 1 into v_lot from public.stock_secretariat;

    for l in select * from json_array_elements(p_lignes) loop
        insert into public.stock_secretariat
            (lot, massier, producteur, n15, n10, auteur_role, secretaire_tel, secretaire_nom)
        values (v_lot, (select massier from public.stock_secretaires where telephone = q ->> 'tel'),
                trim(l ->> 'producteur'),
                coalesce((l ->> 'n15')::integer, 0), coalesce((l ->> 'n10')::integer, 0),
                'SECRETARY', q ->> 'tel', q ->> 'nom');
    end loop;

    return json_build_object('ok', true, 'lignes', v_nb);
end;
$$;

-- ---------------------------------------------------------------------
-- Administrateur : ajouter, modifier, supprimer une ligne du secrétariat.
-- ---------------------------------------------------------------------
create or replace function public.stock_secretariat_ajouter(
    p_massier text, p_producteur text, p_n15 integer, p_n10 integer)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
    v_lot bigint;
    v_massier text := trim(coalesce(p_massier, ''));
    v_prod text := trim(coalesce(p_producteur, ''));
    v_a integer := coalesce(p_n15, 0);
    v_b integer := coalesce(p_n10, 0);
begin
    q := public._stock_qui(null, null);
    if not (q ->> 'ok')::boolean or (q ->> 'role') <> 'ADMIN' then
        return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
    end if;
    if v_massier = '' or length(v_massier) > 80 then
        return json_build_object('ok', false, 'erreur', 'Massier invalide.');
    end if;
    if v_prod = '' or length(v_prod) > 80 then
        return json_build_object('ok', false, 'erreur', 'Nom du producteur invalide.');
    end if;
    if v_a < 0 or v_b < 0 or v_a > 1000000 or v_b > 1000000 or (v_a = 0 and v_b = 0) then
        return json_build_object('ok', false, 'erreur', 'Quantité invalide.');
    end if;

    perform pg_advisory_xact_lock(18002);
    select coalesce(max(lot), 0) + 1 into v_lot from public.stock_secretariat;
    insert into public.stock_secretariat
        (lot, massier, producteur, n15, n10, auteur_role, secretaire_nom)
    values (v_lot, v_massier, v_prod, v_a, v_b, 'ADMIN', q ->> 'nom');
    return json_build_object('ok', true);
end;
$$;

create or replace function public.stock_secretariat_modifier(
    p_id bigint, p_producteur text, p_n15 integer, p_n10 integer)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
    v_prod text := trim(coalesce(p_producteur, ''));
    v_a integer := coalesce(p_n15, 0);
    v_b integer := coalesce(p_n10, 0);
begin
    q := public._stock_qui(null, null);
    if not (q ->> 'ok')::boolean or (q ->> 'role') <> 'ADMIN' then
        return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
    end if;
    if v_prod = '' or length(v_prod) > 80 then
        return json_build_object('ok', false, 'erreur', 'Nom du producteur invalide.');
    end if;
    if v_a < 0 or v_b < 0 or v_a > 1000000 or v_b > 1000000 or (v_a = 0 and v_b = 0) then
        return json_build_object('ok', false, 'erreur', 'Quantité invalide.');
    end if;

    update public.stock_secretariat
       set producteur = v_prod, n15 = v_a, n10 = v_b, modifie_le = now()
     where id = p_id;
    if not found then
        return json_build_object('ok', false, 'erreur', 'Ligne introuvable.');
    end if;
    return json_build_object('ok', true);
end;
$$;

create or replace function public.stock_secretariat_supprimer(p_id bigint)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
begin
    q := public._stock_qui(null, null);
    if not (q ->> 'ok')::boolean or (q ->> 'role') <> 'ADMIN' then
        return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
    end if;
    delete from public.stock_secretariat where id = p_id;
    if not found then
        return json_build_object('ok', false, 'erreur', 'Ligne introuvable.');
    end if;
    return json_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------
-- Droits d'exécution
-- ---------------------------------------------------------------------
revoke all on function public._stock_qui(text, text) from public, anon, authenticated;
revoke all on function public.stock_donnees(text, text) from public;
revoke all on function public.stock_creer_secretaire(text, text, text, text) from public;
revoke all on function public.stock_secretariat_envoyer(text, text, json) from public;
revoke all on function public.stock_secretariat_ajouter(text, text, integer, integer) from public;
revoke all on function public.stock_secretariat_modifier(bigint, text, integer, integer) from public;
revoke all on function public.stock_secretariat_supprimer(bigint) from public;

grant execute on function public.stock_donnees(text, text) to anon, authenticated;
grant execute on function public.stock_creer_secretaire(text, text, text, text) to anon, authenticated;
grant execute on function public.stock_secretariat_envoyer(text, text, json) to anon, authenticated;
grant execute on function public.stock_secretariat_ajouter(text, text, integer, integer) to anon, authenticated;
grant execute on function public.stock_secretariat_modifier(bigint, text, integer, integer) to anon, authenticated;
grant execute on function public.stock_secretariat_supprimer(bigint) to anon, authenticated;
