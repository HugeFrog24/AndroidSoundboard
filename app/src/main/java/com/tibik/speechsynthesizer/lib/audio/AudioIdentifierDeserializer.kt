import com.google.gson.*
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import java.lang.reflect.Type

class AudioIdentifierDeserializer : JsonDeserializer<AudioIdentifier> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AudioIdentifier {
        val jsonObject = json.asJsonObject

        // Determine the type based on the presence of specific fields
        return if (jsonObject.has("id")) {
            // Deserialize as ResourceId
            context.deserialize(json, AudioIdentifier.ResourceId::class.java)
        } else if (jsonObject.has("filename")) {
            // Deserialize as AssetFilename
            context.deserialize(json, AudioIdentifier.AssetFilename::class.java)
        } else {
            throw JsonParseException("Unknown type of AudioIdentifier in JSON")
        }
    }
}