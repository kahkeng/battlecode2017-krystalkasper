import argparse
import collections
import re

r_spawns = re.compile(r'SpawnSignal robotID="(\d+)" .* type="(\w+)" team="(\w)"')
r_deaths = re.compile(r'DeathSignal objectID="(\d+)"')
r_winner = re.compile(r'ser.MatchFooter winner="(\w)"')
r_round = re.compile(r'<ser.RoundDelta>')

TEAMS = ['A', 'B']


def analyze(matchfile):
    spawns = collections.defaultdict(set)  # map of team to robotIDs
    counts = collections.defaultdict(collections.Counter)  # map of team to counts dict
    rounds = 0
    with open(matchfile) as f:
        for line in f:
            line = line.strip()
            m = r_spawns.search(line)
            if m:
                id = m.group(1)
                rtype = m.group(2)
                team = m.group(3)
                spawns[team].add(id)
                counts[team]['spawns'] += 1
                counts[team][rtype] += 1
            m = r_deaths.search(line)
            if m:
                id = m.group(1)
                for team in TEAMS:
                    if id in spawns[team]:
                        counts[team]['deaths'] += 1
            m = r_winner.search(line)
            if m:
                winner = m.group(1)
            m = r_round.search(line)
            if m:
                rounds += 1
    print counts, winner, rounds

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('matchfile')
    args = parser.parse_args()
    analyze(args.matchfile)

if __name__ == "__main__":
    main()
