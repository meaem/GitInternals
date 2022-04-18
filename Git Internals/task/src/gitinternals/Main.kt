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
        fun createObject(c: String): GitObject {
            val lines = c.lines()
            val groups = linePattern.findAll(lines.first())
            if (groups.first().groupValues[1] == "blob") {
                return GitBlobObject(lines.subList(1, lines.size).joinToString("\n"))
            } else {//if (groups.first().groupValues[1] == "commit") {
                return GitCommitObject().also {
                    var nextLinesAreMessage = false
                    it.message = ""
                    it.parent = ""
                    for (line in lines.withIndex()) {
                        if (nextLinesAreMessage) {
                            it.message += line.value + "\n"
                        } else if (line.value.isBlank()) {
                            nextLinesAreMessage = true
                        } else if (line.index > 0) {
                            val linegroups = linePattern.findAll(line.value).first()
                            when (linegroups.groupValues[1]) {
                                "tree" -> it.tree = linegroups.groupValues[2]
                                "parent" -> {
                                    it.parent += (if (it.parent.isEmpty()) "" else " | ") + linegroups.groupValues[2]
                                }
                                "author" -> {
                                    it.author = parseContributor(line.value, "author")
                                }
                                "committer" -> {
                                    it.commiter = parseContributor(line.value, "committer")
                                }

                            }
                        }
                    }
                    it.message = it.message.trimEnd('\n')
                }
            }

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
//                                    val instant = Instant.ofEpochSecond(datetime.toLong())
            val formtter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val datetimeString = formtter.format(instant)
            return "$name $email $commitType timestamp: $datetimeString $zoneSign$zoneHours:$zoneMinuts"
        }
    }
}

class GitBlobObject(val content: String) : GitObject() {
    override fun toString(): String {
        return "*BLOB*\n$content"
    }
}

class GitTreeObject(val content: String) : GitObject() {
    override fun toString(): String {
        return "*TREE*\n$content"
    }
}

class GitCommitObject(

) : GitObject() {
    lateinit var tree: String
    var parent: String = ""
    lateinit var author: String
    lateinit var commiter: String
    lateinit var message: String

    override fun toString(): String {
        return "*COMMIT*\n" +
                "tree: $tree\n" +
                (if (parent.isNotBlank()) "parents: $parent\n" else "") +
                "author: $author\n" +
                "committer: $commiter\n" +
                "commit message:\n$message"
    }
}

val debugFile = File("debug.txt")
fun main() {
//    "+0300".substring(1).split("\\d{2}".toRegex()).map { it.toInt() }
//    val linePattern = "^(\\w+) (.+)$".toRegex()
//    println(linePattern.findAll("commit 216").first().groupValues[1])
    println("Enter .git directory location:")
    val gitDirPath = readln()

    println("Enter git object hash:")
    val objHash = readln()
    val objPath = "$gitDirPath/objects/${objHash.substring(0, 2)}/${objHash.substring(2)}"
//    println(objPath)
    val fis = FileInputStream(objPath)
    val bos1 = ByteArrayOutputStream()
    val ios = InflaterOutputStream(bos1)
    var ch: Int
    while (fis.read().also { ch = it } != -1) {
        ios.write(ch)
    }
    val result = bos1.toByteArray()
    val contentStr = String(result)
        .replace(Char(0), '\n')
    debugFile.writeText(contentStr)

    val gitObj = GitObject.createObject(contentStr)
    println(gitObj)

}