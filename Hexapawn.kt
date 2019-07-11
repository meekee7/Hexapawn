import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
import kotlin.math.abs
import kotlin.random.Random

enum class Field(val rowdiff: Int, val fieldstring: String) {
    EMPTY(0, " "), BLACK(1, "B"), WHITE(-1, "W");

    fun getOpposite() = when (this) {
        EMPTY -> EMPTY
        BLACK -> WHITE
        WHITE -> BLACK
    }

    companion object {
        val COLOURS = listOf(WHITE, BLACK)
    }
}

data class Move(val from: Int, val to: Int) {
    fun isRangeValid() = from in (0..8) && to in (0..8)
    fun isRowGapped() = abs(from / 3 - to / 3) == 1
    fun isValid() = isRangeValid() && (isStraight() || isDiag()) && isRowGapped()
    fun isStraight() = abs(from - to) == 3
    fun isDiag() = abs(from - to) == 2 || abs(from - to) == 4
    fun getDirection() = if (from - to > 0) -1 else 1
}

data class SituationPreferences(private val map: MutableSet<Move>) {
    fun remove(move: Move) = map.remove(move)

    fun pickMove(random: Random) = map.random(random)

    fun isEmpty() = map.isEmpty()
}

data class Board(val grid: List<Field>) {
    init {
        if (grid.size != 9)
            throw IllegalArgumentException()
    }

    fun isValidMove(move: Move): Boolean =
            move.isValid() && grid[move.from] != Field.EMPTY && grid[move.from].rowdiff == move.getDirection()
                    && (!move.isStraight() || grid[move.to] == Field.EMPTY)
                    && (!move.isDiag() || (grid[move.to] != Field.EMPTY && grid[move.to] != grid[move.from]))

    fun makeMove(move: Move): Board {
        if (!this.isValidMove(move))
            throw IllegalArgumentException()

        val newgrid = grid.toMutableList()
        newgrid[move.to] = newgrid[move.from]
        newgrid[move.from] = Field.EMPTY
        return Board(newgrid)
    }

    fun movesFor(colour: Field) = grid.mapIndexed { index, field ->
        if (field != colour) null else
            (2..4).map { index + it * field.rowdiff }
                    .map { Move(index, it) }
                    .filter(this::isValidMove)
    }.filterNotNull().flatten()

    fun hasWinner(playing: Field): Field {
        //Pawn on other side
        if ((0..2).any { grid[it] == Field.WHITE })
            return Field.WHITE
        if ((6..8).any { grid[it] == Field.BLACK })
            return Field.BLACK

        //One piece left
        if (grid.count { it != Field.EMPTY } == 1)
            return grid.first { it != Field.EMPTY }

        //No moves left
        if (movesFor(playing.getOpposite()).isEmpty())
            return playing

        //No winner
        return Field.EMPTY
    }

    fun printBoard() {
        println("|${grid[6].fieldstring}|${grid[7].fieldstring}|${grid[8].fieldstring}|")
        println("|${grid[3].fieldstring}|${grid[4].fieldstring}|${grid[5].fieldstring}|")
        println("|${grid[0].fieldstring}|${grid[1].fieldstring}|${grid[2].fieldstring}|")
    }
}

interface Player {
    fun getMove(board: Board, colour: Field): Move
    fun OnStart(colour: Field) {}
    fun OnEnd(winner: Field) {}
}

class RandomPlayer(val random: Random) : Player {
    override fun getMove(board: Board, colour: Field): Move = board.movesFor(colour).random(random)
}

class LearningPlayer(val random: Random) : Player {
    val data = mapOf<Field, MutableMap<Board, SituationPreferences>>(Field.WHITE to mutableMapOf(), Field.BLACK to mutableMapOf())
    var history = mutableListOf<Pair<Board, Move>>()
    var playingAs = Field.BLACK

    override fun getMove(board: Board, colour: Field): Move {
        data[colour]!!.putIfAbsent(board, SituationPreferences(board.movesFor(colour).toMutableSet()))

        val move = if (data[colour]!![board]!!.isEmpty()) {
            if (history.isEmpty()) //First move
                throw Exception("A strange game. The only winning move is not to play.")
            board.movesFor(colour).random(random)
        } else
            data[colour]!![board]!!.pickMove(random)
        history.add(board to move)
        return move
    }

    override fun OnStart(colour: Field) {
        playingAs = colour
        history = mutableListOf()
    }

    override fun OnEnd(winner: Field) {
        if (winner != playingAs) //Lost this game
            history.reversed().takeWhile {
                data[playingAs]!![it.first]!!.remove(it.second)
                data[playingAs]!![it.first]!!.isEmpty() //Empty means every move would lose, cascade to next move
            }
    }
}

class HumanPlayer : Player {
    override fun getMove(board: Board, colour: Field): Move {
        board.printBoard()
        var fromI: Int? = null
        var toI: Int? = null
        while (fromI == null || toI == null) {
            print("FROM: ")
            fromI = readLine()?.toIntOrNull()
            print("TO: ")
            toI = readLine()?.toIntOrNull()
        }
        return Move(fromI, toI)
    }
}

class GameDirector {
    fun conductGame(white: Player, black: Player): Field {
        var board = Board(listOf(Field.BLACK, Field.BLACK, Field.BLACK, Field.EMPTY, Field.EMPTY, Field.EMPTY, Field.WHITE, Field.WHITE, Field.WHITE))
        val players = mapOf(Field.WHITE to white, Field.BLACK to black)
        players.forEach { (t, u) -> u.OnStart(t) }

        val queue = Field.COLOURS.toMutableList()
        var winner = Field.EMPTY
        while (winner == Field.EMPTY) {
            val current = queue.removeAt(0)
            queue.add(1, current)
            println("Moves for $current: ${board.movesFor(current)}")
            val move = players.getValue(current).getMove(board, current)
            if (board.isValidMove(move) && move.getDirection() == current.rowdiff)
                board = board.makeMove(move)
            else
                throw IllegalStateException("Move not valid for board or player")
            winner = board.hasWinner(current)
        }
        println("WINNER: $winner")
        board.printBoard()
        players.forEach { (_, u) -> u.OnEnd(winner) }
        return winner
    }
}

fun main() {
    var max = 0
    for (i in (1000..1100)) {
//        val white = RandomPlayer(Random(i))
        val white = HumanPlayer()
//        val white = LearningPlayer(Random(i))
        val black = LearningPlayer(Random(500))
        val wins = mutableMapOf(Field.WHITE to 0, Field.BLACK to 0)
        repeat(1000) {
            println("---- NEW GAME ----")
            val winner = GameDirector().conductGame(white, black)
            wins[winner] = wins[winner]!! + 1
            println("WIN STATS: $wins")
            println("Knowledge size for black: ${black.data[Field.BLACK]!!.values.size}")
            max = Math.max(black.data[Field.BLACK]!!.values.size, max)
        }
    }
    println("MAX knowledge size: $max")
}
