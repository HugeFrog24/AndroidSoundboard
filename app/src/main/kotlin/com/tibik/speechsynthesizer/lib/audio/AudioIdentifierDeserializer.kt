import com.google.gson.*
import com.tibik.speechsynthesizer.lib.audio.AudioIdentifier
import java.lang.reflect.Type

class AudioIdentifierDeserializer : JsonDeserializer<AudioIdentifier> {
    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AudioIdentifier {
        val jsonObject = json.asJsonObject

        // Determine the type based on the presence of specific fields
        return when {
            jsonObject.has("id") -> {
                // Deserialize as ResourceId
                context.deserialize(json, AudioIdentifier.ResourceId::class.java)
            }
            jsonObject.has("filename") -> {
                // Deserialize as AssetFilename
                context.deserialize(json, AudioIdentifier.AssetFilename::class.java)
            }
            jsonObject.has("path") -> {
                // Deserialize as FilePath
                context.deserialize(json, AudioIdentifier.FilePath::class.java)
            }
            else -> {
                throw JsonParseException("Unknown type of AudioIdentifier in JSON")
            }
        }
    }
}