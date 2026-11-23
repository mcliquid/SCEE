import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.resolver.Resolver
import java.util.regex.Pattern

/** Country codes like NO must stay strings, not YAML 1.1 boolean false. */
internal class CountryMetadataResolver : Resolver() {
    override fun addImplicitResolvers() {
        addImplicitResolver(Tag.NULL, NULL, null)
        addImplicitResolver(
            Tag.BOOL,
            Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$", Pattern.CASE_INSENSITIVE),
            "tTfF",
        )
        addImplicitResolver(Tag.MERGE, MERGE, "<")
        addImplicitResolver(Tag.INT, INT, "-0123456789")
        addImplicitResolver(Tag.FLOAT, FLOAT, "-0123456789")
        addImplicitResolver(
            Tag.TIMESTAMP,
            Pattern.compile("^([0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].*)"),
            "0123456789",
        )
    }
}