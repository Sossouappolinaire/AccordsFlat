-- Module scolaire : catalogue de niveaux/matières, exercices vérifiés et demandes d'exposé.
-- À exécuter dans Supabase SQL Editor après supabase_setup.sql.
--
-- IMPORTANT : la liste des associations niveau/matière ci-dessous est un inventaire
-- demandé pour préparer la base; elle ne certifie pas que chaque matière est enseignée
-- à chaque niveau. Les statuts restent "a_documenter" jusqu'à vérification du programme.

begin;

create table if not exists public.scolaire_niveaux (
    code text primary key,
    nom text not null,
    ordre smallint not null unique
);

insert into public.scolaire_niveaux (code, nom, ordre) values
    ('CM2', 'CM2', 1),
    ('6E', '6e', 2),
    ('5E', '5e', 3),
    ('4E', '4e', 4),
    ('3E', '3e', 5),
    ('2NDE', 'Seconde', 6),
    ('1ERE', 'Première', 7),
    ('TLE', 'Terminale', 8)
on conflict (code) do update
set nom = excluded.nom, ordre = excluded.ordre;

create table if not exists public.scolaire_matieres (
    code text primary key,
    nom text not null,
    ordre smallint not null unique
);

insert into public.scolaire_matieres (code, nom, ordre) values
    ('mathematiques', 'Mathématiques', 1),
    ('physique', 'Physique', 2),
    ('francais_communication', 'Français — communication écrite', 3),
    ('francais_lecture', 'Français — lecture', 4),
    ('anglais', 'Anglais', 5),
    ('espagnol', 'Espagnol', 6),
    ('eps', 'EPS', 7),
    ('svt', 'Sciences de la vie et de la Terre', 8),
    ('histoire_geographie', 'Histoire-Géographie', 9)
on conflict (code) do update
set nom = excluded.nom, ordre = excluded.ordre;

create table if not exists public.scolaire_programmes (
    code_niveau text not null references public.scolaire_niveaux(code) on delete cascade,
    code_matiere text not null references public.scolaire_matieres(code) on delete cascade,
    ordre smallint not null default 0,
    statut_couverture text not null default 'a_documenter'
        check (statut_couverture in ('a_documenter', 'source_trouvee', 'verifie')),
    reference_source text,
    url_source text,
    version_programme text,
    verifie_le timestamptz,
    primary key (code_niveau, code_matiere)
);

-- Ces lignes servent à présenter le catalogue demandé; elles restent explicitement
-- non vérifiées tant que les documents officiels n'ont pas été associés.
insert into public.scolaire_programmes (code_niveau, code_matiere, ordre, statut_couverture)
select n.code, m.code, m.ordre, 'a_documenter'
from public.scolaire_niveaux n
cross join public.scolaire_matieres m
on conflict (code_niveau, code_matiere) do nothing;

create table if not exists public.scolaire_exercices (
    id uuid primary key default gen_random_uuid(),
    code_niveau text not null references public.scolaire_niveaux(code),
    code_matiere text not null references public.scolaire_matieres(code),
    titre text not null check (char_length(titre) between 3 and 200),
    objectif text not null default '',
    competence text not null default '',
    consigne text not null,
    explication text not null,
    correction text not null,
    difficulte text not null default 'moyen'
        check (difficulte in ('facile', 'moyen', 'avance')),
    source_titre text,
    source_url text,
    reference_programme text,
    version_programme text,
    statut text not null default 'a_verifier'
        check (statut in ('brouillon', 'a_verifier', 'publie', 'archive')),
    cree_le timestamptz not null default now(),
    modifie_le timestamptz not null default now(),
    foreign key (code_niveau, code_matiere)
        references public.scolaire_programmes(code_niveau, code_matiere)
        on delete cascade
);

create index if not exists scolaire_exercices_recherche
    on public.scolaire_exercices (code_niveau, code_matiere, statut, cree_le);

alter table public.scolaire_programmes enable row level security;
alter table public.scolaire_exercices enable row level security;
alter table public.scolaire_niveaux enable row level security;
alter table public.scolaire_matieres enable row level security;

drop policy if exists "scolaire niveaux lecture" on public.scolaire_niveaux;
create policy "scolaire niveaux lecture"
    on public.scolaire_niveaux for select to authenticated
    using (true);

drop policy if exists "scolaire niveaux administration" on public.scolaire_niveaux;
create policy "scolaire niveaux administration"
    on public.scolaire_niveaux for all to authenticated
    using (public.est_admin())
    with check (public.est_admin());

drop policy if exists "scolaire matieres lecture" on public.scolaire_matieres;
create policy "scolaire matieres lecture"
    on public.scolaire_matieres for select to authenticated
    using (true);

drop policy if exists "scolaire matieres administration" on public.scolaire_matieres;
create policy "scolaire matieres administration"
    on public.scolaire_matieres for all to authenticated
    using (public.est_admin())
    with check (public.est_admin());

drop policy if exists "scolaire programmes lecture connectes" on public.scolaire_programmes;
create policy "scolaire programmes lecture connectes"
    on public.scolaire_programmes for select to authenticated
    using (true);

drop policy if exists "scolaire programmes administration" on public.scolaire_programmes;
create policy "scolaire programmes administration"
    on public.scolaire_programmes for all to authenticated
    using (public.est_admin())
    with check (public.est_admin());

drop policy if exists "scolaire exercices publies lecture" on public.scolaire_exercices;
create policy "scolaire exercices publies lecture"
    on public.scolaire_exercices for select to authenticated
    using (statut = 'publie');

drop policy if exists "scolaire exercices administration" on public.scolaire_exercices;
create policy "scolaire exercices administration"
    on public.scolaire_exercices for all to authenticated
    using (public.est_admin())
    with check (public.est_admin());

create table if not exists public.scolaire_exposes (
    id uuid primary key default gen_random_uuid(),
    utilisateur_id uuid not null default auth.uid()
        references auth.users(id) on delete cascade,
    theme text not null check (char_length(theme) between 3 and 300),
    nombre_pages smallint not null check (nombre_pages between 3 and 20),
    plan jsonb not null default '[]'::jsonb,
    -- Tableau JSON des pages 2 à n; la dernière entrée doit être la conclusion.
    contenu_pages jsonb not null default '[]'::jsonb,
    statut text not null default 'demande'
        check (statut in ('demande', 'genere', 'a_revoir', 'termine')),
    cree_le timestamptz not null default now(),
    modifie_le timestamptz not null default now(),
    constraint scolaire_expose_pages_terminees check (
        statut <> 'termine'
        or case
            when jsonb_typeof(plan) <> 'array'
              or jsonb_typeof(contenu_pages) <> 'array' then false
            else jsonb_array_length(plan) > 0
              and jsonb_array_length(contenu_pages) = nombre_pages - 1
        end
    )
);

create index if not exists scolaire_exposes_utilisateur
    on public.scolaire_exposes (utilisateur_id, cree_le desc);

alter table public.scolaire_exposes enable row level security;

drop policy if exists "scolaire exposes lecture proprietaire" on public.scolaire_exposes;
create policy "scolaire exposes lecture proprietaire"
    on public.scolaire_exposes for select to authenticated
    using (utilisateur_id = auth.uid() or public.est_admin());

drop policy if exists "scolaire exposes ajout proprietaire" on public.scolaire_exposes;
create policy "scolaire exposes ajout proprietaire"
    on public.scolaire_exposes for insert to authenticated
    with check (utilisateur_id = auth.uid());

drop policy if exists "scolaire exposes modification proprietaire" on public.scolaire_exposes;
create policy "scolaire exposes modification proprietaire"
    on public.scolaire_exposes for update to authenticated
    using (utilisateur_id = auth.uid() or public.est_admin())
    with check (utilisateur_id = auth.uid() or public.est_admin());

grant select on public.scolaire_niveaux, public.scolaire_matieres,
    public.scolaire_programmes, public.scolaire_exercices to authenticated;
grant insert, update, delete on public.scolaire_niveaux, public.scolaire_matieres,
    public.scolaire_programmes, public.scolaire_exercices to authenticated;
grant select, insert, update on public.scolaire_exposes to authenticated;

commit;
