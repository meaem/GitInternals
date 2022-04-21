package gitinternals

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.InflaterOutputStream

open class GitObject() {
    companion object {
        val linePattern = "^(\\w+) (.+)$".toRegex()
        val authorPattern = "^(\\w+) (\\w+) <(.+)> (\\d+) ([+-])(\\d{2})(\\d{2})$".toRegex()
        fun createObject(fileBytes: ByteArray): GitObject {
            val header = String(fileBytes, 0, fileBytes.indexOf(0))
            val groups = linePattern.findAll(header)
            return if (groups.first().groupValues[1] == "blob") {
                GitBlobObject(fileBytes.copyOfRange(fileBytes.indexOf(0) + 1, fileBytes.size))
            } else if (groups.first().groupValues[1] == "commit") {//if (groups.first().groupValues[1] == "commit") {
                GitCommitObject(fileBytes.copyOfRange(fileBytes.indexOf(0) + 1, fileBytes.size))
            } else {
                GitTreeObject(fileBytes.copyOfRange(fileBytes.indexOf(0) + 1, fileBytes.size))
            }
        }
    }
}

class GitBlobObject(fileBytes: ByteArray) : GitObject() {
    private val content = fileBytes.decodeToString() //skip the header
    override fun toString(): String {
        return "*BLOB*\n$content"
    }
}

class GitTreeObject(fileBytes: ByteArray) : GitObject() {
    private var content = ""

    init {
        var fileBytesCopy = fileBytes.copyOf()
        var lastindex = fileBytesCopy.indexOf(0)


        while (lastindex != -1) {
            val fileBytesCopy2 = fileBytesCopy.copyOfRange(0, lastindex + 21)
            content += decodeTreeLine(fileBytesCopy2) + "\n"
            fileBytesCopy = fileBytesCopy.copyOfRange(lastindex + 21, fileBytesCopy.size)
            lastindex = fileBytesCopy.indexOf(0)
        }
        content = content.trimEnd('\n')
    }

    private fun decodeTreeLine(lineBytes: ByteArray): String {
        val nullIndex = lineBytes.indexOf(0)
        val first = lineBytes.copyOfRange(0, nullIndex).decodeToString().split(" ")
        val second = lineBytes.copyOfRange(nullIndex + 1, lineBytes.size).joinToString("") { String.format("%02x", it) }

        return "${first[0]} $second ${first[1]}"
    }

    override fun toString(): String {
        return "*TREE*\n$content"
    }
}

class GitCommitObject(fileBytes: ByteArray) : GitObject() {
    var message: String
    var parent: String = ""
    lateinit var author: String
    lateinit var commiter: String
    lateinit var tree: String

    init {
        var nextLinesAreMessage = false
        val lines = fileBytes.decodeToString()
            .replace(Char(0), '\n').lines()
        message = ""
        parent = ""
        for (line in lines.withIndex()) {
            if (nextLinesAreMessage) {
                message += line.value + "\n"
            } else if (line.value.isBlank()) {
                nextLinesAreMessage = true
            } else {

                val linegroups = linePattern.findAll(line.value).first()
                when (linegroups.groupValues[1]) {
                    "tree" -> tree = linegroups.groupValues[2]
                    "parent" -> {
                        parent += (if (parent.isEmpty()) "" else " | ") + linegroups.groupValues[2]
                    }
                    "author" -> {
                        author = parseContributor(line.value, "author")
                    }
                    "committer" -> {
                        commiter = parseContributor(line.value, "committer")
                    }

                }
            }
        }
        message = message.trimEnd('\n')
    }

    private fun parseContributor(line: String, type: String): String {
        val commitType = if (type == "committer") "commit" else "original"
        val authGroups = authorPattern.findAll(line).first()
        val name = authGroups.groupValues[2]
        val email = authGroups.groupValues[3]
        val datetime = authGroups.groupValues[4]
        val zoneSign = authGroups.groupValues[5]
        val zoneHours = authGroups.groupValues[6]
        val zoneMinuts = authGroups.groupValues[7]
        val zHours = zoneHours.toInt() * if (zoneSign == "-") -1 else 1
        val zMinuts = zoneMinuts.toInt()

        val instant = LocalDateTime.ofEpochSecond(
            datetime.toLong(),
            0,
            ZoneOffset.ofHoursMinutes(zHours, zMinuts)
        )
        val formtter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val datetimeString = formtter.format(instant)
        return "$name $email $commitType timestamp: $datetimeString $zoneSign$zoneHours:$zoneMinuts"
    }

    override fun toString(): String {
        return "*COMMIT*\n" +
                "tree: $tree\n" +
                (if (parent.isNotBlank()) "parents: $parent\n" else "") +
                "author: $author\n" +
                "committer: $commiter\n" +
                "commit message:\n$message"
    }
}

fun performCatFileCommand(gitDirPath: String) {
    println("Enter git object hash:")
    val objHash = readln()
    val objPath = "$gitDirPath/objects/${objHash.substring(0, 2)}/${objHash.substring(2)}"
    val fis = FileInputStream(objPath)
    val bos1 = ByteArrayOutputStream()
    val ios = InflaterOutputStream(bos1)
    var ch: Int
    while (fis.read().also { ch = it } != -1) {
        ios.write(ch)
    }
    val result = bos1.toByteArray()
    val gitObj = GitObject.createObject(result)
    println(gitObj)
}

fun performListBranchesCommand(gitDirPath: String) {

    val currentBranchFile = File("$gitDirPath/HEAD")

    val currentBranch = if (currentBranchFile.exists()) currentBranchFile
        .readText().trimEnd('\n')
        .split("/").last() else ""
    val f = File("$gitDirPath/refs/heads/")
    val lst = f.list() ?: arrayOf()
    lst.let { l ->
        val branches = l.joinToString("\n") {
            val pre = if (it == currentBranch) "* " else "  "
            pre + it
        }
        println(branches)
    }

}

fun main() {
    println("Enter .git directory location:")
    val gitDirPath = readln()

    println("Enter command:")
    val cmd = readln()
    if (cmd == "cat-file") {
        performCatFileCommand(gitDirPath)
    } else if (cmd == "list-branches") {
        performListBranchesCommand(gitDirPath)
    } else {
        println("???")
    }

}