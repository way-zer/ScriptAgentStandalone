package mindustryProxy.lib.protocol

import io.netty.util.ReferenceCountUtil

fun checkRef(obj: Any) {
    assert(ReferenceCountUtil.refCnt(obj) != 0) { "RefCntError" }
}