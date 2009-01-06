package org.codehaus.jackson.map;

import java.io.*;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.deser.StdDeserializationContext;
import org.codehaus.jackson.map.deser.StdDeserializerProvider;
import org.codehaus.jackson.map.deser.StdDeserializerFactory;
import org.codehaus.jackson.map.ser.StdSerializerProvider;
import org.codehaus.jackson.map.ser.BeanSerializerFactory;
import org.codehaus.jackson.map.type.JavaType;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.map.type.TypeReference;

/**
 * This mapper (or, data binder, or codec) provides functionality for
 * conversting between Java objects (instances of JDK provided core classes,
 * beans), and matching JSON constructs.
 * It will use instances of {@link JsonParser} and {@link JsonGenerator}
 * for implementing actual reading/writing of JSON.
 */
public class ObjectMapper
    extends BaseMapper
{
    /*
    ////////////////////////////////////////////////////
    // Configuration settings
    ////////////////////////////////////////////////////
     */

    /**
     * Object that manages access to serializers used for serialization,
     * including caching.
     * It is configured with {@link #_serializerFactory} to allow
     * for constructing custom serializers.
     */
    protected JsonSerializerProvider _serializerProvider;

    /**
     * Serializer factory used for constructing serializers.
     */
    protected JsonSerializerFactory _serializerFactory;

    /**
     * Object that manages access to deserializers used for deserializing
     * Json content into Java objects, including possible caching
     * of the deserializers.
     * It is configured with {@link #_deserializerFactory} to allow
     * for constructing custom deserializers.
     */
    protected JsonDeserializerProvider _deserializerProvider;

    /**
     * Serializer factory used for constructing deserializers.
     */
    protected JsonDeserializerFactory _deserializerFactory;

    /*
    ////////////////////////////////////////////////////
    // Caching
    ////////////////////////////////////////////////////
     */

    /* Note: handling of serializers and deserializers is not symmetric;
     * and as a result, only root-level deserializers can be cached here.
     * This is mostly because typing and resolution for deserializers is
     * fully static; whereas it is quite dynamic for serialization.
     */

    /**
     * We will use a separate main-level Map for keeping track
     * of root-level deserializers. This is where most succesful
     * cache lookups get resolved.
     * Map will contain resolvers for all kinds of types, including
     * container types: this is different from the component cache
     * which will only cache bean deserializers.
     *<p>
     * Given that we don't expect much concurrency for additions
     * (should very quickly converge to zero after startup), let's
     * explicitly define a low concurrency setting.
     */
    final protected ConcurrentHashMap<JavaType, JsonDeserializer<Object>> _rootDeserializers
        = new ConcurrentHashMap<JavaType, JsonDeserializer<Object>>(64, 0.6f, 2);

    /*
    ////////////////////////////////////////////////////
    // Life-cycle (construction, configuration)
    ////////////////////////////////////////////////////
     */

    /**
     * Default constructor, which will construct the default
     * {@link JsonFactory} as necessary, use
     * {@link StdSerializerProvider} as its
     * {@link JsonSerializerProvider}, and
     * {@link BeanSerializerFactory} as its
     * {@link JsonSerializerFactory}.
     * This means that it
     * can serialize all standard JDK types, as well as regular
     * Java Beans (based on method names and Jackson-specific annotations),
     * but does not support JAXB annotations.
     */
    public ObjectMapper()
    {
        this(null);
    }

    public ObjectMapper(JsonFactory jf)
    {
        this(jf, null, null);
    }

    public ObjectMapper(JsonFactory jf, JsonSerializerProvider sp,
                          JsonDeserializerProvider dp)
    {
        super(jf);
        _serializerProvider = (sp == null) ? new StdSerializerProvider() : sp;
        _deserializerProvider = (dp == null) ? new StdDeserializerProvider() : dp;

        /* Let's just initialize ser/deser factories: they are stateless,
         * no need to create anything, no cost to re-set later on
         */
        _serializerFactory = BeanSerializerFactory.instance;
        _deserializerFactory = StdDeserializerFactory.instance;
    }

    public void setSerializerFactory(JsonSerializerFactory f) {
        _serializerFactory = f;
    }
    public void setDeserializerFactory(JsonDeserializerFactory f) {
        _deserializerFactory = f;
    }

    public void setSerializerProvider(JsonSerializerProvider p) {
        _serializerProvider = p;
    }
    public void setDeserializerProvider(JsonDeserializerProvider p) {
        _deserializerProvider = p;
    }

    /*
    ////////////////////////////////////////////////////
    // Public API: deserialization
    // (mapping from Json to Java types);
    // main methods
    ////////////////////////////////////////////////////
     */

    /**
     * Method to deserialize Json content into a non-container
     * type (it can be an array type, however): typically a bean, array
     * or a wrapper type (like {@link java.lang.Boolean}).
     *<p>
     * Note: this method should NOT be used if the result type is a
     * container ({@link java.util.Collection} or {@link java.util.Map}.
     * The reason is that due to type erasure, key and value types
     * can not be introspected when using this method.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(JsonParser jp, Class<T> valueType)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readValue(jp, TypeFactory.instance.fromClass(valueType));
    } 

    /**
     * Method to deserialize Json content into a Java type, reference
     * to which is passed as argument. Type is passed using so-called
     * "super type token" (see )
     * and specifically needs to be used if the root type is a 
     * parameterized (generic) container type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(JsonParser jp, TypeReference valueTypeRef)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readValue(jp, TypeFactory.instance.fromTypeReference(valueTypeRef));
    } 

    /*
    ////////////////////////////////////////////////////
    // Public API: deserialization
    // (mapping from Json to Java types);
    // additional convenience methods
    ////////////////////////////////////////////////////
     */

    @SuppressWarnings("unchecked")
    public <T> T readValue(File src, Class<T> valueType)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromClass(valueType));
    } 

    @SuppressWarnings("unchecked")
    public <T> T readValue(File src, TypeReference valueTypeRef)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromTypeReference(valueTypeRef));
    } 

    @SuppressWarnings("unchecked")
    public <T> T readValue(URL src, Class<T> valueType)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromClass(valueType));
    } 

    @SuppressWarnings("unchecked")
    public <T> T readValue(URL src, TypeReference valueTypeRef)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromTypeReference(valueTypeRef));
    } 

    @SuppressWarnings("unchecked")
    public <T> T readValue(Reader src, Class<T> valueType)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromClass(valueType));
    } 

    @SuppressWarnings("unchecked")
    public <T> T readValue(Reader src, TypeReference valueTypeRef)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromTypeReference(valueTypeRef));
    } 

    @SuppressWarnings("unchecked")
    public <T> T readValue(InputStream src, Class<T> valueType)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromClass(valueType));
    } 

    @SuppressWarnings("unchecked")
    public <T> T readValue(InputStream src, TypeReference valueTypeRef)
        throws IOException, JsonParseException, JsonMappingException
    {
        return (T) _readMapAndClose(_jsonFactory.createJsonParser(src), TypeFactory.instance.fromTypeReference(valueTypeRef));
    } 

    /*
    ////////////////////////////////////////////////////
    // Public API: serialization
    // (mapping from Java types to Json)
    ////////////////////////////////////////////////////
     */

    /**
     * Method that can be used to serialize any Java value as
     * Json output, using provided {@link JsonGenerator}.
     */
    public void writeValue(JsonGenerator jgen, Object value)
        throws IOException, JsonGenerationException, JsonMappingException
    {
        _serializerProvider.serializeValue(jgen, value, _serializerFactory);
        jgen.flush();
    }

    /**
     * Method that can be used to serialize any Java value as
     * Json output, written to File provided.
     */
    public void writeValue(File resultFile, Object value)
        throws IOException, JsonGenerationException, JsonMappingException
    {
        JsonGenerator jgen = _jsonFactory.createJsonGenerator(resultFile, JsonEncoding.UTF8);
        boolean closed = false;
        try {
            writeValue(jgen, value);
            closed = true;
            jgen.close();
        } finally {
            /* won't try to close twice; also, must catch exception (to it 
             * will not mask exception that is pending)
            */
            if (!closed) {
                try {
                    jgen.close();
                } catch (IOException ioe) { }
            }
        }
    }

    /**
     * Method that can be used to serialize any Java value as
     * Json output, using output stream provided (using encoding
     * {link JsonEncoding#UTF8}).
     *<p>
     * Note: method does not close the underlying stream explicitly
     * here; however, {@link JsonFactory} this mapper uses may choose
     * to close the stream depending on its settings (by default,
     * it will try to close it when {@link JsonGenerator} we construct
     * is closed).
     */
    public void writeValue(OutputStream out, Object value)
        throws IOException, JsonGenerationException, JsonMappingException
    {
        JsonGenerator jgen = _jsonFactory.createJsonGenerator(out, JsonEncoding.UTF8);
        boolean closed = false;
        try {
            writeValue(jgen, value);
            closed = true;
            jgen.close();
        } finally {
            if (!closed) {
                jgen.close();
            }
        }
    }

    /**
     * Method that can be used to serialize any Java value as
     * Json output, using Writer provided.
     *<p>
     * Note: method does not close the underlying stream explicitly
     * here; however, {@link JsonFactory} this mapper uses may choose
     * to close the stream depending on its settings (by default,
     * it will try to close it when {@link JsonGenerator} we construct
     * is closed).
     */
    public void writeValue(Writer w, Object value)
        throws IOException, JsonGenerationException, JsonMappingException
    {
        JsonGenerator jgen = _jsonFactory.createJsonGenerator(w);
        boolean closed = false;
        try {
            writeValue(jgen, value);
            closed = true;
            jgen.close();
        } finally {
            if (!closed) {
                jgen.close();
            }
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, exposing Java constructs as JSON
    // event source via JSONParser
    //
    // NOT YET IMPLEMENTED -- will they even be added
    // here, or somewhere else?
    ////////////////////////////////////////////////////
     */

    /**
     * Method that will take in a Java object that could have
     * been created by mappers write methods, and construct
     * a {@link JsonParser} that exposes contents as JSON
     * tokens
     */
    /*
    public JsonParser createParserFor(Object data)
        throws JsonParseException
    {
        // !!! TBI: parser for reading from Object (array/map, primitives)
        return null;
    }
    */

    /**
     * Method that will create a JSON generator that will build
     * Java objects as members of the current list, appending
     * them at the end of the list.
     */
    /*
    public JsonGenerator createGeneratorFor(List<?> context)
        throws JsonGenerationException
    {
        // !!! TBI: generator for writing (appending) to Json Arrays (Java lists)
        return null;
    }
    */

    /*
    public JsonGenerator createGeneratorFor(Map<?,?> context)
        throws JsonParseException
    {
        // !!! TBI: generator for writing (appending) to Json Objects (Java maps)
        return null;
    }
    */

    /*
    ////////////////////////////////////////////////////
    // Internal methods, overridable
    ////////////////////////////////////////////////////
     */

    protected Object _readValue(JsonParser jp, JavaType valueType)
        throws IOException, JsonParseException, JsonMappingException
    {
        _initForReading(jp);
        JsonDeserializationContext ctxt = _createDeserializationContext(jp);
        // ok, let's get the value
        Object result = _findDeserializer(valueType).deserialize(jp, ctxt);
        // and then need to skip past the last event before returning
        jp.nextToken();
        return result;
    }

    protected Object _readMapAndClose(JsonParser jp, JavaType valueType)
        throws IOException, JsonParseException, JsonMappingException
    {
        _initForReading(jp);
        JsonDeserializationContext ctxt = _createDeserializationContext(jp);
        try {
            Object result = _findDeserializer(valueType).deserialize(jp, ctxt);
            // and then need to skip past the last event before returning
            jp.nextToken();
            return result;
        } finally {
            try {
                jp.close();
            } catch (IOException ioe) { }
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods, other
    ////////////////////////////////////////////////////
     */

    protected JsonDeserializer<Object> _findDeserializer(JavaType valueType)
    {
        // First: have we already seen it?
        JsonDeserializer<Object> deser = _rootDeserializers.get(valueType);
        if (deser != null) {
            return deser;
        }

        // Nope: need to ask provider to resolve it
        deser = _deserializerProvider.findValueDeserializer(valueType, _deserializerFactory);
        if (deser == null) { // can this happen?
            throw new IllegalArgumentException("Can not find a deserializer for type "+valueType);
        }
        _rootDeserializers.put(valueType, deser);
        return deser;
    }

    protected JsonDeserializationContext _createDeserializationContext(JsonParser jp)
    {
        return new StdDeserializationContext(jp);
    }

    /**
     * Method called to ensure that given parser is ready for reading content
     * for data binding.
     */
    protected void _initForReading(JsonParser jp)
        throws IOException, JsonGenerationException, JsonMappingException
    {
        // First: must point to a token; if not point to one, advance
        if (!jp.hasCurrentToken()) {
            // and then we must get something...
            if (jp.nextToken() == null) {
                throw JsonMappingException.from(jp, "No content available via given JsonParser");
            }
        }
    }
}