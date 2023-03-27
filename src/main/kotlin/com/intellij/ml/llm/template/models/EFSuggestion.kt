package com.intellij.ml.llm.template.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure

@kotlinx.serialization.Serializable data class EFSuggestion(
    var functionName: String,
    var lineStart: Int,
    var lineEnd: Int
)

object EFSuggestionSerializer : KSerializer<EFSuggestion> {
    override fun deserialize(decoder: Decoder): EFSuggestion {
        return decoder.decodeStructure(descriptor) {
            var functionName = ""
            var lineStart = 0
            var lineEnd = 0
            while (true) {
                when(val index = decodeElementIndex(descriptor)) {
                    0 -> functionName = decodeStringElement(descriptor, index)
                    1 -> lineStart = decodeIntElement(descriptor, index)
                    2 -> lineEnd = decodeIntElement(descriptor, index)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("unexpected index: $index")
                }
            }
            EFSuggestion(functionName, lineStart, lineEnd)
        }
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("EFSuggestion") {
        element("functionName", String.serializer().descriptor)
        element("lineStart", Int.serializer().descriptor)
        element("lineEnd", Int.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: EFSuggestion) {
        TODO("Not yet implemented")
    }

}
