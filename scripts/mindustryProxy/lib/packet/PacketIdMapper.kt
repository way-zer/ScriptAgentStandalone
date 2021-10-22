package mindustryProxy.lib.packet

import mindustryProxy.lib.Server

object PacketIdMapper {
    const val UNKNOWN = "UNKNOWN"
    private var id2Name = emptyArray<String?>()
    private var name2Id = emptyMap<String, Int>()

    fun load() {
        val list = javaClass.classLoader.getResourceAsStream("remoteId.csv")?.use { reader ->
            reader.reader().useLines { lines ->
                lines.associate {
                    val sp = it.split("\t")
                    sp[0].toInt() to sp[1]
                }
            }
        }
        if (list.isNullOrEmpty()) {
            Server.logger.warning("Fail to load remoteId.csv")
            return
        }
        id2Name = Array(list.keys.maxOrNull()!! + 1) { list[it] }
        name2Id = list.map { it.value to it.key }.toMap()
        Server.logger.info("Loaded remoteId.csv: " + list.size)
    }

    /**@return [UNKNOWN] when unknown */
    fun id2Name(id: Int): String {
        val res = id2Name.getOrNull(id) ?: UNKNOWN
        if (res == UNKNOWN) Server.logger.warning("unknown packet id with $id")
        return res
    }

    fun getIdByName(name: String): Int? {
        val res = name2Id[name]
        if (res == null) Server.logger.warning("unknown packet id named $name")
        return res
    }

    operator fun get(name: String): Int = getIdByName(name)!!
    operator fun get(id: Int): String = id2Name(id)

    val idIndices get() = id2Name.indices

    init {
        load()
    }
}