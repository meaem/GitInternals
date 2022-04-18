package gitinternals

import java.io.File
import java.util.zip.Inflater

fun main() {
    println("Enter git object location:")
    val objPath = readln()
    val objFile = File(objPath)
    val content = objFile.readBytes()
    val result = ByteArray(1024)
    val decompressor = Inflater()
    decompressor.setInput(content)
    val actualNum = decompressor.inflate(result)
    decompressor.end()
    val contentStr = String(result,0,actualNum-1)
        .replace(Char(0),'\n')
    println(contentStr)
}
