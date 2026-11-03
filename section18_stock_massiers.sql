-- =====================================================================
-- SECTION 18 : GESTION DE LA CARRIÈRE (Stock Massiers)
-- À exécuter une seule fois dans Supabase (SQL Editor).
--
--  * L'administrateur (table admins, fonction est_admin()) crée les comptes
--    propriétaires : nom et prénom + numéro de téléphone + code PIN.
--  * Le propriétaire se connecte dans l'appli avec son numéro (à la place de
--    l'email) et son PIN (à la place du mot de passe).
--  * Chaque production (administrateur) et chaque vente (propriétaire) est
--    enregistrée avec la date, l'heure et le nom de son auteur.
--
-- Les tables sont fermées (RLS sans règle) : tout passe par les fonctions
-- stock_* ci-dessous, qui vérifient qui appelle (administrateur connecté,
-- ou propriétaire avec numéro + PIN). Après 5 PIN faux, le numéro est bloqué
-- 15 minutes.
-- =====================================================================

create table if not exists public.stock_proprietaires (
    telephone text primary key,
    nom       text not null,
    pin       text not null,
    cree_le   timestamptz not null default now(),
    cree_par  text
);

create table if not exists public.stock_operations (
    id          bigserial primary key,
    type        text not null check (type in ('PROD', 'SALE')),
    massier     text not null,
    n15         integer not null default 0 check (n15 >= 0),
    n10         integer not null default 0 check (n10 >= 0),
    auteur_role text not null check (auteur_role in ('ADMIN', 'OWNER')),
    auteur_tel  text,
    auteur_nom  text not null,
    cree_le     timestamptz not null default now()
);

create table if not exists public.stock_essais (
    telephone text primary key,
    echecs    integer not null default 0,
    dernier   timestamptz not null default now()
);

alter table public.stock_proprietaires enable row level security;
alter table public.stock_operations    enable row level security;
alter table public.stock_essais        enable row level security;

revoke all on public.stock_proprietaires from anon, authenticated;
revoke all on public.stock_operations    from anon, authenticated;
revoke all on public.stock_essais        from anon, authenticated;
revoke all on sequence public.stock_operations_id_seq from anon, authenticated;

-- ---------------------------------------------------------------------
-- Qui appelle ? Administrateur (jeton Supabase) ou propriétaire (numéro + PIN).
-- Fonction interne : non appelable depuis l'appli.
-- ---------------------------------------------------------------------
create or replace function public._stock_qui(p_tel text, p_pin text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    v_nom text;
    v_essais record;
    v_existe boolean;
begin
    if public.est_admin() then
        select nom into v_nom from public.profils where id = auth.uid();
        return json_build_object('ok', true, 'role', 'ADMIN', 'tel', '',
                                 'nom', coalesce(nullif(trim(v_nom), ''), 'Administrateur'));
    end if;

    p_tel := regexp_replace(coalesce(p_tel, ''), '\s', '', 'g');
    v_existe := exists (select 1 from public.stock_proprietaires where telephone = p_tel);

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
-- Connexion (propriétaire : numéro + PIN ; administrateur : jeton).
-- ---------------------------------------------------------------------
create or replace function public.stock_connexion(p_tel text, p_pin text)
returns json
language sql
security definer
set search_path = public
as $$
    select public._stock_qui(p_tel, p_pin);
$$;

-- ---------------------------------------------------------------------
-- Toutes les données : opérations (la plus récente d'abord) et, pour
-- l'administrateur seulement, la liste des propriétaires.
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
begin
    q := public._stock_qui(p_tel, p_pin);
    if not (q ->> 'ok')::boolean then return q; end if;
    v_role := q ->> 'role';

    return json_build_object(
        'ok', true,
        'role', v_role,
        'nom', q ->> 'nom',
        'entrees', coalesce((
            select json_agg(json_build_object(
                       'type', o.type, 'massier', o.massier, 'n15', o.n15, 'n10', o.n10,
                       'ts', to_char(o.cree_le at time zone 'Africa/Porto-Novo', 'DD/MM/YYYY HH24:MI:SS'),
                       'par', o.auteur_nom, 'role', o.auteur_role)
                   order by o.id desc)
              from public.stock_operations o), '[]'::json),
        'proprietaires', case when v_role = 'ADMIN' then coalesce((
            select json_agg(json_build_object(
                       'tel', p.telephone, 'nom', p.nom,
                       'cree', to_char(p.cree_le at time zone 'Africa/Porto-Novo', 'DD/MM/YYYY HH24:MI:SS'))
                   order by p.cree_le desc)
              from public.stock_proprietaires p), '[]'::json)
            else '[]'::json end
    );
end;
$$;

-- ---------------------------------------------------------------------
-- Ajouter une opération : production (administrateur) ou vente (propriétaire).
-- ---------------------------------------------------------------------
create or replace function public.stock_ajouter(
    p_tel text, p_pin text, p_type text, p_massier text, p_n15 integer, p_n10 integer)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
    v_role text;
    v_massier text;
    v_n15 integer := coalesce(p_n15, 0);
    v_n10 integer := coalesce(p_n10, 0);
    s15 integer;
    s10 integer;
begin
    q := public._stock_qui(p_tel, p_pin);
    if not (q ->> 'ok')::boolean then return q; end if;
    v_role := q ->> 'role';

    p_type := upper(coalesce(p_type, ''));
    if p_type not in ('PROD', 'SALE') then
        return json_build_object('ok', false, 'erreur', 'Type d''opération inconnu.');
    end if;
    if (p_type = 'PROD' and v_role <> 'ADMIN') or (p_type = 'SALE' and v_role <> 'OWNER') then
        return json_build_object('ok', false, 'erreur', 'Action non autorisée pour ce compte.');
    end if;

    v_massier := trim(coalesce(p_massier, ''));
    if v_massier = '' or length(v_massier) > 80 then
        return json_build_object('ok', false, 'erreur', 'Nom du massier invalide.');
    end if;
    if v_n15 < 0 or v_n10 < 0 or v_n15 > 1000000 or v_n10 > 1000000 or (v_n15 = 0 and v_n10 = 0) then
        return json_build_object('ok', false, 'erreur', 'Quantité invalide.');
    end if;

    perform pg_advisory_xact_lock(18001);   -- une opération à la fois (stock toujours juste)

    if p_type = 'SALE' then
        select coalesce(sum(case when type = 'PROD' then n15 else -n15 end), 0),
               coalesce(sum(case when type = 'PROD' then n10 else -n10 end), 0)
          into s15, s10
          from public.stock_operations where massier = v_massier;
        if v_n15 > s15 or v_n10 > s10 then
            return json_build_object('ok', false, 'erreur',
                'Stock insuffisant chez ' || v_massier || ' : ' || s15 || ' de 5/15 et ' || s10 || ' de 5/10');
        end if;
    end if;

    insert into public.stock_operations (type, massier, n15, n10, auteur_role, auteur_tel, auteur_nom)
    values (p_type, v_massier, v_n15, v_n10, v_role, nullif(q ->> 'tel', ''), q ->> 'nom');

    return json_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------
-- Créer un compte propriétaire (administrateur seulement).
-- ---------------------------------------------------------------------
create or replace function public.stock_creer_proprietaire(p_nom text, p_numero text, p_code text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    q json;
    v_nom text := trim(coalesce(p_nom, ''));
    v_num text := regexp_replace(coalesce(p_numero, ''), '\s', '', 'g');
begin
    q := public._stock_qui(null, null);
    if not (q ->> 'ok')::boolean or (q ->> 'role') <> 'ADMIN' then
        return json_build_object('ok', false, 'erreur', 'Réservé à l''administrateur.');
    end if;

    if length(v_nom) < 2 or length(v_nom) > 80 then
        return json_build_object('ok', false, 'erreur', 'Entrez le nom et prénom du propriétaire.');
    end if;
    if v_num !~ '^\+?[0-9]{8,15}$' then
        return json_build_object('ok', false, 'erreur', 'Numéro de téléphone invalide.');
    end if;
    if coalesce(p_code, '') !~ '^[0-9]{4}$' then
        return json_build_object('ok', false, 'erreur', 'Le PIN doit avoir 4 chiffres.');
    end if;

    insert into public.stock_proprietaires (telephone, nom, pin, cree_par)
    values (v_num, v_nom, p_code, q ->> 'nom');
    return json_build_object('ok', true);
exception when unique_violation then
    return json_build_object('ok', false, 'erreur', 'Ce numéro existe déjà.');
end;
$$;

-- ---------------------------------------------------------------------
-- Droits d'exécution : seules les 4 fonctions publiques sont appelables.
-- ---------------------------------------------------------------------
revoke all on function public._stock_qui(text, text) from public, anon, authenticated;
revoke all on function public.stock_connexion(text, text) from public;
revoke all on function public.stock_donnees(text, text) from public;
revoke all on function public.stock_ajouter(text, text, text, text, integer, integer) from public;
revoke all on function public.stock_creer_proprietaire(text, text, text) from public;

grant execute on function public.stock_connexion(text, text) to anon, authenticated;
grant execute on function public.stock_donnees(text, text) to anon, authenticated;
grant execute on function public.stock_ajouter(text, text, text, text, integer, integer) to anon, authenticated;
grant execute on function public.stock_creer_proprietaire(text, text, text) to anon, authenticated;
