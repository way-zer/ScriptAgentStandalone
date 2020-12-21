import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.random.Random

data class SrvRecord(val ttl: Int, val priority: Short, val weight: Short, val port: Int, val target: String)

fun getSrvRecord(dnsHost: String, name: String): SrvRecord {
    val id = Random.nextInt()
    //build request pocket
    val data = ByteArrayOutputStream().apply {
        DataOutputStream(this).use { stream ->
            stream.writeShort(id)
            stream.writeByte(1)//Recursion
            stream.writeByte(0)
            stream.writeShort(1)
            stream.writeShort(0)
            stream.writeShort(0)
            stream.writeShort(0)
            //endHeader
            name.split('.').forEach {
                stream.writeByte(it.length)
                it.forEach { c ->
                    stream.writeByte(c.toInt())
                }
            }
            stream.writeByte(0)
            //end Name
            stream.writeShort(33)//SRV
            stream.writeShort(1)//Internet
        }
    }
    //request dns server
    val socket = DatagramSocket()
    socket.send(DatagramPacket(data.toByteArray(), data.size(), InetAddress.getByName(dnsHost), 53))
    val res = ByteArray(512)
    socket.receive(DatagramPacket(res, 512))
    //resolve response from dns server
    ByteBuffer.wrap(res).apply {
        //short following is ByteBuffer.getShort()
        assert(short.toInt() == id)
        position(data.size())//Pass Header and QRecord
        assert(short == (0xc00c).toShort()) { "Lookup fail" }
        assert(short == (33).toShort())//SRV
        assert(short == (1).toShort())//Internet
        val ttl = int
        short//Data Length,ignore
        return SrvRecord(ttl, short, short, short.toInt(), buildString {
            var l: Int
            do {
                l = get().toInt()
                repeat(l) {
                    append(get().toChar())
                }
                append('.')
            } while (l != 0)
            delete(length - 2, length)
        })
    }
}

fun main() {
    val name = "_minecraft._tcp.mc.rog.ink"
    println(getSrvRecord("8.8.8.8", name))
}