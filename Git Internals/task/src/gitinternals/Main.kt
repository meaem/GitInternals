package gitinternals

import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.zip.InflaterOutputStream


fun main() {
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
    contentStr.lines().first().let {
        it.split(" ").let {
            println("type:${it[0]} length:${it[1]}")
        }
    }

}
