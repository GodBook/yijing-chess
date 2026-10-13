"""Validate actual YiJing exports using python-chess and sgfmill, not the app's importer."""
import io
import json
import sys
from pathlib import Path

import chess.pgn
from sgfmill import sgf, sgf_moves


def count_pgn_nodes(node):
    return 1 + sum(count_pgn_nodes(child) for child in node.variations)


def count_sgf_nodes(node):
    return 1 + sum(count_sgf_nodes(child) for child in node)


def verify(directory):
    expected = json.loads((directory / "expected.json").read_text(encoding="utf-8"))
    for case in expected:
        source = directory / case["file"]
        if source.suffix == ".pgn":
            game = chess.pgn.read_game(io.StringIO(source.read_text(encoding="utf-8")))
            assert game is not None and not game.errors, (source.name, game.errors)
            assert game.end().board().fen(en_passant="fen") == case["state"], source.name
            assert len(list(game.mainline_moves())) == case["moves"], source.name
            assert count_pgn_nodes(game) == case["nodes"], source.name
        else:
            game = sgf.Sgf_game.from_bytes(source.read_bytes())
            board, moves = sgf_moves.get_setup_and_moves(game)
            assert len(moves) == case["moves"], source.name
            assert count_sgf_nodes(game.get_root()) == case["nodes"], source.name
            if case["comment"]:
                assert game.get_root().get("C") == case["comment"], source.name
            for colour, move in moves:
                if move is not None:
                    board.play(*move, colour)
            state = "".join((board.get(board.side - 1 - y, x) or ".").upper()
                            for y in range(board.side) for x in range(board.side))
            assert state == case["state"], source.name
        print(f"PASS {source.name}: {case['moves']} main-line moves, {case['nodes']} nodes")
    print(f"Independent notation validation: {len(expected)} fixtures passed")


if __name__ == "__main__":
    verify(Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/test-results/verifyChessRules/notation"))
