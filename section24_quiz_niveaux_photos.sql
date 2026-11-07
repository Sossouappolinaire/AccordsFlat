-- =====================================================================
--  24) QUESTIONS POUR UN CHAMPION : 1224 questions, points 4-3-2-1, photos des joueurs, questions jamais répétées,
--      salon jusqu'à 10 joueurs — relançable (à exécuter dans Supabase > SQL Editor après section22_quiz.sql)
--
--   A) quiz_questions : questions 127 à 1224 (les textes sont dans QuizBanque.java, même numéro)
--   B) quiz_vues       : questions déjà posées à chaque joueur ; quiz_demarrer ne repropose jamais une question déjà
--                        vue par l'un des joueurs du salon (quand tout a été vu, on repart de zéro pour ces joueurs)
--   C) Points « 4-3-2-1 » : la bonne réponse donne la valeur de l'instant où elle arrive au serveur
--                        (4 pendant le premier quart du temps, puis 3, 2 et 1) ; le premier à cliquer garde le point
--   D) Photo de profil : profils.photo (petite image JPEG en base64), quiz_photo_definir(), quiz_photos(salon)
--   E) quiz_rejoindre : 10 joueurs au maximum
-- =====================================================================

-- A) Questions 127 à 1224
insert into public.quiz_questions (id, bonne) values
(127,0),(128,0),(129,2),(130,3),(131,1),(132,1),(133,0),(134,2),(135,0),(136,2),(137,0),(138,1),
(139,3),(140,0),(141,0),(142,2),(143,0),(144,2),(145,3),(146,2),(147,0),(148,3),(149,3),(150,0),
(151,1),(152,2),(153,0),(154,2),(155,0),(156,0),(157,2),(158,3),(159,0),(160,3),(161,2),(162,1),
(163,0),(164,0),(165,3),(166,3),(167,3),(168,3),(169,2),(170,2),(171,0),(172,0),(173,0),(174,3),
(175,3),(176,2),(177,3),(178,0),(179,0),(180,0),(181,0),(182,2),(183,0),(184,1),(185,3),(186,1),
(187,3),(188,2),(189,1),(190,3),(191,2),(192,0),(193,0),(194,2),(195,0),(196,1),(197,0),(198,0),
(199,2),(200,3),(201,1),(202,0),(203,1),(204,2),(205,0),(206,1),(207,3),(208,3),(209,2),(210,3),
(211,0),(212,0),(213,3),(214,0),(215,0),(216,0),(217,0),(218,1),(219,3),(220,0),(221,0),(222,1),
(223,3),(224,3),(225,0),(226,3),(227,3),(228,0),(229,0),(230,2),(231,3),(232,1),(233,3),(234,2),
(235,3),(236,3),(237,2),(238,2),(239,0),(240,3),(241,1),(242,3),(243,2),(244,3),(245,0),(246,0),
(247,3),(248,2),(249,1),(250,0),(251,3),(252,1),(253,0),(254,0),(255,3),(256,2),(257,3),(258,1),
(259,0),(260,2),(261,2),(262,0),(263,1),(264,3),(265,2),(266,0),(267,1),(268,1),(269,0),(270,0),
(271,2),(272,2),(273,2),(274,1),(275,0),(276,0),(277,3),(278,3),(279,3),(280,2),(281,1),(282,0),
(283,0),(284,0),(285,2),(286,1),(287,0),(288,3),(289,2),(290,0),(291,3),(292,0),(293,3),(294,0),
(295,0),(296,0),(297,3),(298,1),(299,0),(300,3),(301,3),(302,2),(303,3),(304,3),(305,1),(306,0),
(307,0),(308,2),(309,0),(310,1),(311,0),(312,3),(313,1),(314,1),(315,0),(316,2),(317,2),(318,1),
(319,2),(320,3),(321,2),(322,1),(323,2),(324,1),(325,2),(326,1),(327,2),(328,2),(329,2),(330,2),
(331,1),(332,1),(333,1),(334,2),(335,0),(336,3),(337,3),(338,3),(339,1),(340,1),(341,1),(342,3),
(343,0),(344,0),(345,2),(346,2),(347,2),(348,2),(349,2),(350,1),(351,1),(352,1),(353,2),(354,2),
(355,2),(356,1),(357,3),(358,0),(359,2),(360,3),(361,2),(362,1),(363,3),(364,1),(365,0),(366,1),
(367,2),(368,0),(369,3),(370,1),(371,2),(372,1),(373,2),(374,2),(375,1),(376,3),(377,1),(378,0),
(379,0),(380,3),(381,0),(382,3),(383,0),(384,0),(385,2),(386,2),(387,2),(388,3),(389,0),(390,2),
(391,0),(392,1),(393,1),(394,2),(395,1),(396,0),(397,1),(398,2),(399,2),(400,2),(401,3),(402,1),
(403,0),(404,3),(405,3),(406,3),(407,3),(408,0),(409,2),(410,1),(411,2),(412,0),(413,1),(414,2),
(415,2),(416,1),(417,0),(418,2),(419,0),(420,2),(421,3),(422,3),(423,3),(424,2),(425,0),(426,3),
(427,2),(428,1),(429,3),(430,1),(431,2),(432,0),(433,1),(434,0),(435,0),(436,1),(437,3),(438,2),
(439,0),(440,0),(441,0),(442,1),(443,2),(444,0),(445,3),(446,2),(447,2),(448,1),(449,3),(450,0),
(451,1),(452,2),(453,1),(454,2),(455,2),(456,2),(457,3),(458,2),(459,0),(460,1),(461,3),(462,2),
(463,1),(464,3),(465,1),(466,0),(467,1),(468,1),(469,2),(470,3),(471,1),(472,1),(473,3),(474,1),
(475,0),(476,0),(477,3),(478,0),(479,2),(480,0),(481,3),(482,0),(483,0),(484,0),(485,2),(486,3),
(487,2),(488,0),(489,0),(490,1),(491,0),(492,3),(493,1),(494,2),(495,1),(496,0),(497,0),(498,1),
(499,3),(500,3),(501,2),(502,3),(503,2),(504,3),(505,2),(506,0),(507,3),(508,3),(509,1),(510,2),
(511,2),(512,1),(513,2),(514,3),(515,0),(516,2),(517,3),(518,3),(519,3),(520,3),(521,3),(522,0),
(523,2),(524,0),(525,3),(526,0),(527,0),(528,0),(529,2),(530,1),(531,1),(532,2),(533,3),(534,0),
(535,3),(536,1),(537,3),(538,0),(539,1),(540,2),(541,0),(542,1),(543,1),(544,0),(545,2),(546,1),
(547,3),(548,0),(549,1),(550,2),(551,2),(552,0),(553,0),(554,1),(555,2),(556,3),(557,2),(558,2),
(559,0),(560,3),(561,3),(562,2),(563,1),(564,2),(565,3),(566,3),(567,3),(568,2),(569,3),(570,2),
(571,2),(572,1),(573,2),(574,3),(575,2),(576,2),(577,0),(578,2),(579,2),(580,0),(581,3),(582,2),
(583,3),(584,0),(585,0),(586,3),(587,0),(588,3),(589,0),(590,1),(591,0),(592,0),(593,0),(594,0),
(595,1),(596,3),(597,1),(598,2),(599,0),(600,3),(601,0),(602,1),(603,0),(604,3),(605,0),(606,1),
(607,1),(608,0),(609,3),(610,3),(611,1),(612,3),(613,1),(614,2),(615,0),(616,3),(617,2),(618,1),
(619,2),(620,3),(621,3),(622,2),(623,1),(624,1),(625,2),(626,2),(627,3),(628,0),(629,3),(630,2),
(631,3),(632,3),(633,3),(634,0),(635,0),(636,3),(637,0),(638,1),(639,0),(640,1),(641,0),(642,3),
(643,2),(644,2),(645,3),(646,0),(647,1),(648,2),(649,0),(650,3),(651,2),(652,3),(653,1),(654,0),
(655,1),(656,1),(657,0),(658,2),(659,3),(660,0),(661,0),(662,1),(663,0),(664,0),(665,3),(666,1),
(667,3),(668,2),(669,3),(670,2),(671,0),(672,3),(673,1),(674,1),(675,3),(676,0),(677,0),(678,1),
(679,3),(680,3),(681,3),(682,1),(683,0),(684,3),(685,0),(686,2),(687,1),(688,0),(689,2),(690,3),
(691,0),(692,0),(693,2),(694,0),(695,3),(696,1),(697,3),(698,2),(699,2),(700,3),(701,3),(702,1),
(703,0),(704,1),(705,1),(706,2),(707,0),(708,3),(709,0),(710,3),(711,2),(712,0),(713,1),(714,3),
(715,0),(716,1),(717,0),(718,0),(719,0),(720,0),(721,1),(722,1),(723,2),(724,0),(725,3),(726,2),
(727,1),(728,1),(729,1),(730,1),(731,0),(732,2),(733,1),(734,3),(735,0),(736,3),(737,1),(738,0),
(739,2),(740,0),(741,3),(742,2),(743,0),(744,3),(745,1),(746,3),(747,3),(748,1),(749,3),(750,2),
(751,1),(752,1),(753,2),(754,3),(755,1),(756,3),(757,0),(758,2),(759,3),(760,2),(761,3),(762,3),
(763,0),(764,1),(765,1),(766,2),(767,2),(768,2),(769,1),(770,3),(771,0),(772,3),(773,1),(774,2),
(775,3),(776,2),(777,0),(778,1),(779,2),(780,1),(781,0),(782,1),(783,3),(784,1),(785,1),(786,3),
(787,3),(788,0),(789,1),(790,3),(791,0),(792,3),(793,1),(794,0),(795,1),(796,0),(797,1),(798,3),
(799,3),(800,1),(801,2),(802,1),(803,2),(804,2),(805,2),(806,0),(807,2),(808,2),(809,3),(810,2),
(811,0),(812,0),(813,2),(814,0),(815,1),(816,1),(817,1),(818,1),(819,0),(820,0),(821,1),(822,3),
(823,2),(824,1),(825,3),(826,3),(827,3),(828,0),(829,3),(830,2),(831,0),(832,1),(833,1),(834,3),
(835,0),(836,3),(837,0),(838,0),(839,3),(840,0),(841,1),(842,0),(843,3),(844,0),(845,0),(846,3),
(847,0),(848,0),(849,0),(850,1),(851,0),(852,1),(853,3),(854,0),(855,2),(856,0),(857,0),(858,3),
(859,2),(860,1),(861,2),(862,2),(863,1),(864,2),(865,1),(866,1),(867,0),(868,2),(869,2),(870,2),
(871,0),(872,0),(873,0),(874,3),(875,1),(876,2),(877,0),(878,2),(879,3),(880,3),(881,1),(882,3),
(883,2),(884,2),(885,0),(886,0),(887,2),(888,1),(889,1),(890,1),(891,3),(892,0),(893,2),(894,0),
(895,3),(896,3),(897,0),(898,1),(899,3),(900,2),(901,1),(902,3),(903,1),(904,3),(905,2),(906,3),
(907,1),(908,2),(909,2),(910,1),(911,2),(912,1),(913,1),(914,1),(915,2),(916,2),(917,3),(918,1),
(919,2),(920,0),(921,3),(922,0),(923,3),(924,1),(925,1),(926,3),(927,2),(928,2),(929,0),(930,0),
(931,0),(932,3),(933,2),(934,3),(935,0),(936,2),(937,1),(938,0),(939,1),(940,1),(941,3),(942,2),
(943,1),(944,3),(945,3),(946,3),(947,0),(948,2),(949,0),(950,0),(951,3),(952,2),(953,3),(954,1),
(955,0),(956,1),(957,1),(958,1),(959,3),(960,3),(961,2),(962,2),(963,3),(964,1),(965,3),(966,3),
(967,0),(968,1),(969,0),(970,0),(971,2),(972,1),(973,0),(974,0),(975,3),(976,0),(977,3),(978,1),
(979,2),(980,1),(981,2),(982,1),(983,2),(984,0),(985,2),(986,1),(987,3),(988,0),(989,3),(990,2),
(991,0),(992,1),(993,1),(994,0),(995,1),(996,0),(997,2),(998,0),(999,0),(1000,1),(1001,1),(1002,0),
(1003,1),(1004,3),(1005,1),(1006,3),(1007,3),(1008,2),(1009,3),(1010,1),(1011,2),(1012,2),(1013,0),(1014,1),
(1015,3),(1016,0),(1017,1),(1018,0),(1019,1),(1020,2),(1021,2),(1022,0),(1023,0),(1024,2),(1025,2),(1026,0),
(1027,1),(1028,2),(1029,1),(1030,1),(1031,3),(1032,1),(1033,2),(1034,2),(1035,2),(1036,1),(1037,2),(1038,1),
(1039,1),(1040,3),(1041,2),(1042,3),(1043,0),(1044,1),(1045,2),(1046,2),(1047,0),(1048,3),(1049,3),(1050,3),
(1051,2),(1052,0),(1053,2),(1054,3),(1055,3),(1056,3),(1057,2),(1058,0),(1059,3),(1060,0),(1061,3),(1062,2),
(1063,3),(1064,0),(1065,0),(1066,0),(1067,2),(1068,1),(1069,2),(1070,3),(1071,0),(1072,3),(1073,3),(1074,1),
(1075,2),(1076,2),(1077,1),(1078,1),(1079,2),(1080,0),(1081,1),(1082,0),(1083,0),(1084,0),(1085,1),(1086,3),
(1087,3),(1088,2),(1089,1),(1090,1),(1091,3),(1092,0),(1093,3),(1094,1),(1095,1),(1096,1),(1097,0),(1098,1),
(1099,3),(1100,0),(1101,1),(1102,3),(1103,0),(1104,0),(1105,1),(1106,3),(1107,3),(1108,1),(1109,3),(1110,0),
(1111,2),(1112,1),(1113,3),(1114,3),(1115,1),(1116,0),(1117,3),(1118,2),(1119,0),(1120,0),(1121,2),(1122,1),
(1123,0),(1124,2),(1125,0),(1126,2),(1127,1),(1128,0),(1129,2),(1130,3),(1131,0),(1132,0),(1133,2),(1134,0),
(1135,3),(1136,2),(1137,2),(1138,1),(1139,3),(1140,3),(1141,3),(1142,3),(1143,1),(1144,2),(1145,3),(1146,3),
(1147,3),(1148,1),(1149,3),(1150,3),(1151,0),(1152,2),(1153,1),(1154,0),(1155,2),(1156,3),(1157,3),(1158,3),
(1159,0),(1160,2),(1161,2),(1162,3),(1163,2),(1164,2),(1165,1),(1166,1),(1167,3),(1168,0),(1169,2),(1170,0),
(1171,0),(1172,1),(1173,0),(1174,1),(1175,3),(1176,1),(1177,3),(1178,2),(1179,2),(1180,2),(1181,2),(1182,3),
(1183,1),(1184,1),(1185,0),(1186,0),(1187,2),(1188,1),(1189,3),(1190,0),(1191,0),(1192,3),(1193,0),(1194,3),
(1195,3),(1196,3),(1197,1),(1198,0),(1199,3),(1200,3),(1201,0),(1202,0),(1203,0),(1204,3),(1205,1),(1206,2),
(1207,2),(1208,0),(1209,0),(1210,0),(1211,3),(1212,2),(1213,1),(1214,3),(1215,1),(1216,3),(1217,3),(1218,0),
(1219,1),(1220,3),(1221,2),(1222,2),(1223,3),(1224,2)
on conflict (id) do update set bonne = excluded.bonne;

-- B) Questions déjà posées
create table if not exists public.quiz_vues (
    user_id     uuid    not null,
    question_id integer not null,
    vu_le       timestamptz not null default now(),
    primary key (user_id, question_id)
);
alter table public.quiz_vues enable row level security;      -- aucun accès direct : tout passe par les fonctions

create or replace function public.quiz_demarrer(p_salon uuid)
returns json language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    s public.quiz_salons;
    v_ids integer[];
    v_debut timestamptz := clock_timestamp() + interval '22 seconds';
begin
    if v_uid is null then return json_build_object('ok', false, 'erreur', 'Connexion requise.'); end if;
    select * into s from public.quiz_salons where id = p_salon for update;
    if not found or s.hote <> v_uid then return json_build_object('ok', false, 'erreur', 'Seul l''hôte peut lancer la partie.'); end if;
    if s.etat <> 'attente' then return json_build_object('ok', true); end if;
    if (select count(*) from public.quiz_joueurs where salon = s.id) < 2 then
        return json_build_object('ok', false, 'erreur', 'Il faut au moins 2 joueurs.');
    end if;
    -- uniquement des questions que personne dans le salon n'a encore reçues
    select array_agg(id) into v_ids from (
        select q.id from public.quiz_questions q
         where not exists (select 1 from public.quiz_vues v
                             join public.quiz_joueurs j on j.user_id = v.user_id and j.salon = s.id
                            where v.question_id = q.id)
         order by random() limit s.nb) t;
    if v_ids is null or array_length(v_ids, 1) < s.nb then
        -- tout a déjà été vu : on repart de zéro pour les joueurs de ce salon
        delete from public.quiz_vues where user_id in (select user_id from public.quiz_joueurs where salon = s.id);
        select array_agg(id) into v_ids from (select id from public.quiz_questions order by random() limit s.nb) t;
    end if;
    if v_ids is null or array_length(v_ids, 1) < s.nb then
        return json_build_object('ok', false, 'erreur', 'Pas assez de questions.');
    end if;
    insert into public.quiz_vues (user_id, question_id)
        select j.user_id, x from public.quiz_joueurs j, unnest(v_ids) x where j.salon = s.id
        on conflict do nothing;
    update public.quiz_salons set etat = 'en_cours', questions = v_ids, debut = v_debut,
           num = 0, q_debut = v_debut, q_fin = null, q_gagnant = null, q_gagnant_nom = null
     where id = s.id;
    return json_build_object('ok', true);
end $$;

-- C) Points 4-3-2-1 selon l'instant de la bonne réponse
create or replace function public.quiz_repondre(p_salon uuid, p_num integer, p_choix integer)
returns json language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    s public.quiz_salons;
    v_now timestamptz;
    v_bonne smallint;
    v_ms integer;
    v_val integer;
    v_score integer;
    n_rep integer;
    n_joueurs integer;
begin
    if v_uid is null then return json_build_object('ok', false, 'erreur', 'Connexion requise.'); end if;
    if not exists (select 1 from public.quiz_joueurs where salon = p_salon and user_id = v_uid) then
        return json_build_object('ok', false, 'erreur', 'Partie introuvable.');
    end if;
    perform public._quiz_avancer(p_salon);
    select * into s from public.quiz_salons where id = p_salon for update;
    if not found or s.etat <> 'en_cours' then return json_build_object('ok', false, 'erreur', 'Partie terminée.'); end if;
    if p_choix is null or p_choix < 0 or p_choix > 3 then return json_build_object('ok', false, 'erreur', 'Choix invalide.'); end if;
    v_now := clock_timestamp();
    if p_num is null or p_num <> s.num or v_now < s.q_debut then
        return json_build_object('ok', false, 'erreur', 'Question terminée.');
    end if;
    if s.q_fin is not null then
        return json_build_object('ok', false, 'tard', true, 'gagnant', s.q_gagnant_nom,
            'erreur', case when s.q_gagnant_nom is null then 'Temps écoulé.' else s.q_gagnant_nom || ' a été plus rapide.' end);
    end if;
    if exists (select 1 from public.quiz_reponses where salon = p_salon and user_id = v_uid and num = p_num) then
        return json_build_object('ok', false, 'erreur', 'Vous avez déjà répondu.');
    end if;

    select bonne into v_bonne from public.quiz_questions where id = s.questions[s.num + 1];
    v_ms := floor(extract(epoch from (v_now - s.q_debut)) * 1000)::integer;
    -- 4 points pendant le premier quart du temps, puis 3, 2 et 1
    v_val := case when v_ms < s.duree * 250 then 4 when v_ms < s.duree * 500 then 3 when v_ms < s.duree * 750 then 2 else 1 end;

    if p_choix = v_bonne then
        insert into public.quiz_reponses (salon, user_id, num, choix, pts, ms) values (p_salon, v_uid, p_num, p_choix, v_val, v_ms);
        update public.quiz_joueurs set score = score + v_val, rep = rep + 1
         where salon = p_salon and user_id = v_uid returning score into v_score;
        update public.quiz_salons set q_fin = v_now, q_gagnant = v_uid, q_gagnant_nom = public._quiz_nom(v_uid)
         where id = p_salon;
        return json_build_object('ok', true, 'juste', true, 'premier', true, 'bonne', v_bonne, 'score', v_score, 'ms', v_ms, 'valeur', v_val);
    end if;

    insert into public.quiz_reponses (salon, user_id, num, choix, pts, ms) values (p_salon, v_uid, p_num, p_choix, 0, v_ms);
    update public.quiz_joueurs set rep = rep + 1 where salon = p_salon and user_id = v_uid;
    select count(*) into n_joueurs from public.quiz_joueurs where salon = p_salon;
    select count(*) into n_rep from public.quiz_reponses where salon = p_salon and num = p_num;
    if n_rep >= n_joueurs then
        update public.quiz_salons set q_fin = v_now where id = p_salon;
    end if;
    return json_build_object('ok', true, 'juste', false);
end $$;

-- D) Photos de profil
alter table public.profils add column if not exists photo text;
do $$ begin
    if not exists (select 1 from pg_constraint where conname = 'profils_photo_taille') then
        alter table public.profils add constraint profils_photo_taille check (photo is null or length(photo) <= 30000);
    end if;
end $$;

create or replace function public.quiz_photo_definir(p_photo text)
returns json language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then return json_build_object('ok', false, 'erreur', 'Connexion requise.'); end if;
    if p_photo is not null and length(p_photo) > 30000 then return json_build_object('ok', false, 'erreur', 'Photo trop grande.'); end if;
    update public.profils set photo = nullif(p_photo, '') where id = auth.uid();
    return json_build_object('ok', true);
end $$;

-- Photos des joueurs d'un salon : {"<cle>": "<photo en base64>"} (la clé est la même que « cle » dans quiz_etat)
create or replace function public.quiz_photos(p_salon uuid)
returns json language plpgsql stable security definer set search_path = public as $$
begin
    if auth.uid() is null or not exists (select 1 from public.quiz_joueurs where salon = p_salon and user_id = auth.uid()) then
        return json_build_object('ok', false);
    end if;
    return json_build_object('ok', true, 'photos', coalesce((
        select json_object_agg(md5(j.user_id::text), p.photo)
          from public.quiz_joueurs j join public.profils p on p.id = j.user_id
         where j.salon = p_salon and p.photo is not null), '{}'::json));
end $$;

-- quiz_etat : ajoute la clé de chaque joueur (pour retrouver sa photo)
create or replace function public.quiz_etat(p_salon uuid)
returns json language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    s public.quiz_salons;
begin
    if v_uid is null then return json_build_object('ok', false, 'erreur', 'Connexion requise.'); end if;
    if not exists (select 1 from public.quiz_joueurs where salon = p_salon and user_id = v_uid) then
        return json_build_object('ok', false, 'erreur', 'Salon introuvable ou fermé.');
    end if;
    update public.quiz_joueurs set vu = now() where salon = p_salon and user_id = v_uid;
    perform public._quiz_avancer(p_salon);
    select * into s from public.quiz_salons where id = p_salon;
    if not found then return json_build_object('ok', false, 'erreur', 'Salon introuvable ou fermé.'); end if;
    if s.etat = 'fini' then
        perform public._quiz_attribuer(s.id);
    end if;
    return json_build_object(
        'ok', true, 'etat', s.etat, 'code', s.code, 'hote', s.hote = v_uid, 'nb', s.nb, 'duree', s.duree, 'pause', 4,
        'gain', public._quiz_regle('gain_victoire', 25),
        'maintenant', (extract(epoch from clock_timestamp()) * 1000)::bigint,
        'debut', case when s.debut is null then null else (extract(epoch from s.debut) * 1000)::bigint end,
        'num', s.num,
        'q_debut', case when s.q_debut is null then null else (extract(epoch from s.q_debut) * 1000)::bigint end,
        'q_fin', case when s.q_fin is null then null else (extract(epoch from s.q_fin) * 1000)::bigint end,
        'gagnant', s.q_gagnant_nom,
        'gagnant_moi', coalesce(s.q_gagnant = v_uid, false),
        'questions', to_json(s.questions),
        'joueurs', coalesce((select json_agg(json_build_object('nom', j.nom, 'score', j.score, 'rep', j.rep, 'moi', j.user_id = v_uid,
                                                               'cle', md5(j.user_id::text))
                                   order by j.score desc, j.nom)
                               from public.quiz_joueurs j where j.salon = p_salon), '[]'::json),
        'victoire', (select json_build_object('nom', v.nom, 'points', v.points, 'motif', v.motif, 'moi', v.user_id = v_uid)
                       from public.quiz_victoires v where v.salon = p_salon));
end $$;

-- E) Salon jusqu'à 10 joueurs
create or replace function public.quiz_rejoindre(p_code text)
returns json language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    s public.quiz_salons;
begin
    if v_uid is null then return json_build_object('ok', false, 'erreur', 'Connectez-vous pour jouer en ligne.'); end if;
    select * into s from public.quiz_salons where code = upper(btrim(coalesce(p_code, '')));
    if not found then return json_build_object('ok', false, 'erreur', 'Salon introuvable.'); end if;
    if exists (select 1 from public.quiz_joueurs where salon = s.id and user_id = v_uid) then
        return json_build_object('ok', true, 'salon', s.id, 'code', s.code);
    end if;
    if s.etat <> 'attente' then return json_build_object('ok', false, 'erreur', 'Cette partie a déjà commencé.'); end if;
    if (select count(*) from public.quiz_joueurs where salon = s.id) >= 10 then
        return json_build_object('ok', false, 'erreur', 'Le salon est complet (10 joueurs).');
    end if;
    insert into public.quiz_joueurs (salon, user_id, nom) values (s.id, v_uid, public._quiz_nom(v_uid));
    return json_build_object('ok', true, 'salon', s.id, 'code', s.code);
end $$;

revoke all on function public.quiz_demarrer(uuid), public.quiz_repondre(uuid, integer, integer), public.quiz_etat(uuid),
    public.quiz_rejoindre(text), public.quiz_photo_definir(text), public.quiz_photos(uuid) from public, anon;
grant execute on function public.quiz_demarrer(uuid), public.quiz_repondre(uuid, integer, integer), public.quiz_etat(uuid),
    public.quiz_rejoindre(text), public.quiz_photo_definir(text), public.quiz_photos(uuid) to authenticated;
