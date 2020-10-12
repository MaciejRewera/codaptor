package tech.b180.cordaptor.rest

import nonapi.io.github.classgraph.json.JSONSerializer
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.bind
import org.koin.dsl.module
import tech.b180.cordaptor.kernel.*
import kotlin.reflect.KClass

/**
 * Implementation of the microkernel module provider that makes the components
 * of this module available for injection into other modules' components.
 *
 * This class is instantiated by the microkernel at runtime using [java.util.ServiceLoader].
 */
@Suppress("UNUSED")
class RestEndpointModuleProvider : ModuleProvider {
  override val salience = 100

  override fun provideModule(settings: BootstrapSettings) = module {
    single {
      JettyConnectorConfiguration(
        bindAddress = getHostAndPortProperty("listen.address")
      )
    }
    single { JettyServer() } bind LifecycleAware::class

    single { ConnectorFactory(get()) } bind JettyConfigurator::class

    // definitions for various context handlers
    single { ApiDefinitionHandler("/api.json") } bind ContextMappedHandler::class
    single { SwaggerUIHandler("/swagger-ui.html") } bind ContextMappedHandler::class
    single { NodeInfoHandler("/node/info") } bind ContextMappedHandler::class
    single { TransactionQueryHandler("/node/tx") } bind ContextMappedHandler::class
    single { VaultQueryHandler("/node/states") } bind ContextMappedHandler::class
    single { CountingVaultQueryHandler("/node/statesCount") } bind ContextMappedHandler::class
    single { AggregatingVaultQueryHandler("/node/statesTotalAmount") } bind ContextMappedHandler::class

    // contributes handlers for specific flow and state endpoints
    single { NodeStateApiProvider("/node") } bind ContextMappedHandlerFactory::class

    // JSON serialization enablement
    single { SerializationFactory(lazyGetAll()) }

    single { CordaX500NameSerializer() } bind CustomSerializer::class
    single { CordaSecureHashSerializer() } bind CustomSerializer::class
    single { CordaUUIDSerializer() } bind CustomSerializer::class
    single { CordaPartySerializer(get(), get()) } bind CustomSerializer::class
    single { CordaSignedTransactionSerializer(get(), get()) } bind CustomSerializer::class
    single { CordaPartyAndCertificateSerializer(get()) } bind CustomSerializer::class
    single { JavaInstantSerializer() } bind CustomSerializer::class
    single { ThrowableSerializer(get()) } bind CustomSerializer::class

    // factory for requesting specific serializers into the non-generic serialization code
    factory<JsonSerializer<*>> { (key: SerializerKey) -> get<SerializationFactory>().getSerializer(key) }
  }
}

/**
 * A shorthand to be used in the client code requesting a JSON serializer
 * to be injected using given type information
 */
fun <T: Any> Koin.getSerializer(clazz: KClass<T>, vararg typeParameters: KClass<*>) =
    get<JsonSerializer<T>> { parametersOf(SerializerKey(clazz, *typeParameters)) }

/**
 * A shorthand to be used by an instance of [CordaptorComponent] requesting a JSON serializer
 * to be injected using given type information
 */
fun <T: Any> CordaptorComponent.injectSerializer(clazz: KClass<T>, vararg typeParameters: KClass<*>): Lazy<JsonSerializer<T>> =
    lazy { getKoin().get<JsonSerializer<T>> { parametersOf(SerializerKey(clazz, *typeParameters)) } }