"""Construit l'asset SQLite des villes (reverse geocoding + matching des requêtes).

Source : dumps GeoNames (CC BY 4.0) — https://download.geonames.org/export/dump/
  - citiesNNNN.zip       : les villes (id, nom, pays, coordonnées, population)
  - alternateNamesV2.zip : les exonymes par langue (« Moscou » pour « Moscow »)

Pourquoi des alias : la colonne `name` de GeoNames est le nom **international**,
en pratique l'anglais quand il en existe un — « Rome », « Vienna », « Munich », et
non « Roma », « Wien », « München ». (Mesuré sur les dumps réels le 2026-07-15 ; la
croyance inverse, « GeoNames stocke la forme locale », traînait dans le backlog et
est fausse.)

Mais l'anglais lui-même a plusieurs formes par ville, et **une seule est
canonique** : Mumbai/Bombay, Beijing/Peking, Ho Chi Minh City/Saigon,
Kolkata/Calcutta. Les requêtes étant destinées à être traduites en anglais avant
d'atteindre le parser, c'est le **maximum de variantes anglaises** qu'il faut
indexer : le traducteur — comme l'utilisateur — peut sortir n'importe laquelle.
D'où le défaut `--langs en`, historiques et familiers **inclus**.

Usage (uv, aucune dépendance hors stdlib) :
    uv run --python 3.12 tools/build_cities_db.py \
        --cities cities5000.zip --alternate alternateNamesV2.zip \
        --out app/src/main/assets/cities5000.db

    # sans alternateNames : la colonne 3 de citiesNNNN.txt sert de repli
    uv run --python 3.12 tools/build_cities_db.py --cities cities5000.zip --out cities.db

Les .zip sont lus tels quels, inutile de les décompresser.

Sortie : deux tables, `cities` et `city_aliases`, recopiées telles quelles dans Room
par CityImportWorker.
"""
import argparse
import csv
import io
import os
import sqlite3
import sys
import unicodedata
import zipfile

# Langue des alias : celle dans laquelle le parser VOIT la requête, pas celle dans
# laquelle l'utilisateur la tape. Le pipeline traduira tout en anglais avant de
# parser (les personnes, ensemble fermé, étant extraites avant) — un seul jeu de
# variantes à indexer, quelle que soit la langue de l'utilisateur. Ajouter `fr` ne
# servirait qu'à un pipeline sans traduction.
DEFAULT_LANGS = "en"

def normalize(text: str) -> str:
    """Reproduit `QueryParser.normalize` : NFD, marques retirées, œ/æ dépliés.

    Doit rester **fidèle** à la version Kotlin : c'est elle qui décide si un alias
    apprend quelque chose au matching. Une divergence ne casserait rien bruyamment,
    elle laisserait juste passer des alias inutiles — ou en écarterait d'utiles.
    """
    folded = unicodedata.normalize("NFD", text.lower())
    folded = "".join(c for c in folded if not unicodedata.combining(c))
    return folded.replace("œ", "oe").replace("æ", "ae")


def open_member(path: str, suffix: str) -> io.TextIOWrapper:
    """Ouvre un .txt, ou le bon membre d'un .zip GeoNames.

    « Le bon » n'est pas le premier : alternateNamesV2.zip liste
    `iso-languagecodes.txt` (0,1 Mo) AVANT `alternateNamesV2.txt` (777 Mo). Prendre
    le premier membre venu ne planterait pas — il produirait **zéro alias en
    silence**. On cible donc le membre homonyme de l'archive, et à défaut le plus
    gros.
    """
    if not zipfile.is_zipfile(path):
        return open(path, encoding="utf-8", newline="")
    archive = zipfile.ZipFile(path)
    members = [m for m in archive.infolist() if m.filename.endswith(suffix)]
    if not members:
        raise SystemExit(f"{path} ne contient aucun membre {suffix} : {archive.namelist()}")

    stem = os.path.splitext(os.path.basename(path))[0].lower()
    named = next(
        (m for m in members if os.path.splitext(m.filename)[0].lower() == stem), None
    )
    chosen = named or max(members, key=lambda m: m.file_size)
    return io.TextIOWrapper(archive.open(chosen), encoding="utf-8", newline="")


def rows(stream) -> csv.reader:
    # GeoNames n'échappe rien et ses noms contiennent des guillemets (« Xi'an »,
    # des noms arabes avec `"`) : QUOTE_NONE est obligatoire, sinon csv avale des
    # lignes entières en croyant lire un champ cité.
    return csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE)


def read_cities(path: str) -> dict:
    cities = {}
    with open_member(path, ".txt") as stream:
        for row in rows(stream):
            if len(row) < 15:
                continue
            geonameid = int(row[0])
            cities[geonameid] = {
                "name": row[1],
                "country": row[8],
                "lat": float(row[4]),
                "lon": float(row[5]),
                "population": int(row[14]) if row[14] else None,
                "fallback": row[3],  # colonne « alternatenames », sans langue
            }
    return cities


def read_aliases(path: str, cities: dict, langs: set, current_only: bool) -> list:
    """Alias depuis alternateNamesV2 : la source de qualité, filtrée par langue.

    Les colonnes 0-7 sont communes à V1 et V2 (V2 ajoute from/to), on n'indexe donc
    jamais au-delà de 7.

    Historiques et familiers sont **gardés** par défaut, à rebours de l'intuition :
    ces alias ne servent qu'au **matching**, jamais à l'affichage, et « Bombay »,
    « Saigon » ou « Peking » sont précisément ce que les gens tapent encore. Les
    écarter ne protégerait de rien et coûterait du rappel. [current_only] existe
    pour un usage où les alias seraient affichés.
    """
    found = []
    with open_member(path, ".txt") as stream:
        for row in rows(stream):
            if len(row) < 4:
                continue
            geonameid = int(row[1])
            city = cities.get(geonameid)
            if city is None:
                continue
            if row[2] not in langs:
                continue
            if current_only:
                is_colloquial = len(row) > 6 and row[6] == "1"
                is_historic = len(row) > 7 and row[7] == "1"
                if is_colloquial or is_historic:
                    continue
            alias = row[3].strip()
            if alias:
                found.append((geonameid, alias))
    return found


def read_fallback_aliases(cities: dict) -> list:
    """Repli sans alternateNames : la colonne 3 de citiesNNNN.txt.

    Elle liste les exonymes **sans code de langue**, donc toutes langues et tous
    alphabets confondus. On ne garde que le latin : sans langue, impossible de
    distinguer un exonyme français d'un nom chinois, et ces derniers ne peuvent de
    toute façon pas matcher une requête fr/en.
    """
    found = []
    for geonameid, city in cities.items():
        for alias in city["fallback"].split(","):
            alias = alias.strip()
            if alias and all(ord(c) < 0x250 or not c.isalpha() for c in alias):
                found.append((geonameid, alias))
    return found


def build(cities: dict, aliases: list, out: str) -> tuple:
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript(
        """
        CREATE TABLE cities (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            country_code TEXT NOT NULL,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            population INTEGER
        );
        CREATE TABLE city_aliases (
            city_id INTEGER NOT NULL,
            alias TEXT NOT NULL,
            PRIMARY KEY (city_id, alias)
        );
        """
    )
    db.executemany(
        "INSERT INTO cities VALUES (?, ?, ?, ?, ?, ?)",
        [
            (i, c["name"], c["country"], c["lat"], c["lon"], c["population"])
            for i, c in cities.items()
        ],
    )

    kept, seen = [], set()
    for geonameid, alias in aliases:
        key = (geonameid, normalize(alias))
        if key in seen:
            continue
        # L'alias qui redit le nom canonique n'ajoute rien : c'est ce filtre qui
        # évite de stocker « Paris » pour Paris, soit l'écrasante majorité des villes.
        if key[1] == normalize(cities[geonameid]["name"]):
            continue
        seen.add(key)
        kept.append((geonameid, alias))

    db.executemany("INSERT INTO city_aliases VALUES (?, ?)", kept)
    db.commit()
    db.execute("VACUUM")
    db.close()
    return len(cities), len(kept)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cities", required=True, help="citiesNNNN.txt ou .zip")
    parser.add_argument("--alternate", help="alternateNamesV2.txt ou .zip (recommandé)")
    parser.add_argument("--out", default="cities.db")
    parser.add_argument("--langs", default=DEFAULT_LANGS, help=f"défaut : {DEFAULT_LANGS}")
    parser.add_argument(
        "--current-only",
        action="store_true",
        help="écarte les noms historiques et familiers (Bombay, Saigon…), gardés par défaut",
    )
    args = parser.parse_args()

    langs = {l.strip() for l in args.langs.split(",") if l.strip()}
    cities = read_cities(args.cities)
    if not cities:
        raise SystemExit(f"aucune ville lue depuis {args.cities}")

    if args.alternate:
        aliases = read_aliases(args.alternate, cities, langs, args.current_only)
        variants = "courants seulement" if args.current_only else "toutes variantes"
        source = f"alternateNames ({','.join(sorted(langs))}, {variants})"
    else:
        aliases = read_fallback_aliases(cities)
        source = "colonne 3 de citiesNNNN (repli, latin seulement)"
        print("ATTENTION : sans --alternate, les alias ne sont pas filtrés par langue", file=sys.stderr)

    n_cities, n_aliases = build(cities, aliases, args.out)
    size = os.path.getsize(args.out)
    print(f"source alias : {source}")
    print(f"RESULT {args.out} villes={n_cities} alias={n_aliases} bytes={size} ({size / 1e6:.1f} Mo)")


if __name__ == "__main__":
    main()
