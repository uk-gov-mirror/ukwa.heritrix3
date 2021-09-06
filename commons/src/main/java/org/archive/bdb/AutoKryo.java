package org.archive.bdb;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objenesis.strategy.StdInstantiatorStrategy;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Extensions to Kryo to let classes control their own registration, suggest
 * other classes to register together, and use the same (Sun-JVM-only) trick
 * for deserializing classes without no-arg constructors.
 * 
 * newInstance technique and constructor caching inspired by the 
 * KryoReflectionFactorySupport class of Martin Grotzke's kryo-serializers 
 * project. <a href="https://github.com/magro/kryo-serializers">https://github.com/magro/kryo-serializers</a>
 * 
 * 
 * org.archive.crawler.selftest.StatisticsSelfTest
 * TODO: more comments!
 * 
 * @author gojomo
 */
public class AutoKryo extends Kryo {
    protected ArrayList<Class<?>> registeredClasses = new ArrayList<Class<?>>(); 
    
    public AutoKryo() {
    	// Some classes don't have suitable constructors, so we need additional tricks to create them:
    	DefaultInstantiatorStrategy is = new Kryo.DefaultInstantiatorStrategy();
    	is.setFallbackInstantiatorStrategy(new StdInstantiatorStrategy());
    	this.setInstantiatorStrategy(is);

    	// Doing this allows classes to be registered as we come across them (see note below on limitations):
        this.setRegistrationRequired(false);
        // n.b. There is no point setting this in autoregister hooks as it's a global setting.
        
    	// Heritrix's structure means we can't know all required classes at this point, but we 
        // can handle platform classes here.
        
        /*
         * Custom serializer because default serialization doesn't work. Any
         * non-null IP address comes back as 0.0.0.0. XXX Inet4Address also
         * holds hostname, but heritrix doesn't use that; and retrieving it can
         * result in dns lookup, so we don't serialize it.
         * 
         * This was originally autoregisted in org.archive.modules.net.CrawlHost.autoregister().
         */
        this.register(Inet4Address.class, new Serializer<Inet4Address>() {

			@Override
			public void write(Kryo kryo, Output output, Inet4Address object) {
				byte[] address = object.getAddress();
				output.writeInt(address.length);
                output.write(address);
			}

			@Override
			public Inet4Address read(Kryo kryo, Input input, Class<Inet4Address> type) {
                try {
                	int length = input.readInt();
                	byte[] address = input.readBytes(length);
                    return (Inet4Address) InetAddress.getByAddress(address);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
			}

        });
        
    }
    
    // From version 2 onwards, it is no longer safe to use a single Kryo instance. 
    // See https://github.com/EsotericSoftware/kryo#thread-safety
    // This has significant consequences for Heritrix3, and how class registration is used.
    //
	// Auto-registration functioning as expected requires the order of registration to be the same on every run.
    // As some classes are still picked up and runtime, this leads to errors like:
    // 
    //     com.esotericsoftware.kryo.KryoException: Encountered unregistered class ID: 14
    //
    // Therefore, the initial use of this class is commented out in org.archive.bdb.KryoBinding
    //
    // The auto-registration system also allows different classes to register the same sub-class, but potentially 
    // with different configuration.  This may lead to confusing behaviour.
    //
    // To make it work, it would be necessary to force registration to be required, and then go through and 
    // make sure every used class is declared in the relevant autoregister function. Furthermore, The order of
    // registration has to be maintained to ensure compatability over time.
    //
    public void autoregister(Class<?> type) {
        if (registeredClasses.contains(type)) {
            return;
        }
        registeredClasses.add(type); 
        try {
            invokeStatic(
                "autoregisterTo", 
                type,
                new Class[]{ ((Class<?>)AutoKryo.class), }, 
                new Object[] { this, });
        } catch (Exception e) {
            register(type); 
        }
    }

    //protected static final ReflectionFactory REFLECTION_FACTORY = ReflectionFactory.getReflectionFactory();
    protected static final Object[] INITARGS = new Object[0];
    protected static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<Class<?>, Constructor<?>>();

    protected Object invokeStatic(String method, Class<?> clazz, Class<?>[] types, Object[] args) throws Exception {
        return clazz.getMethod(method, types).invoke(null, args);
    }

    /*
    @Override
    public <T> T newInstance(Class<T> type) {
        com.esotericsoftware.kryo.KryoException ex = null;
        try {
            return super.newInstance(type);
        } catch (com.esotericsoftware.kryo.KryoException se) {
            ex = se;
        }
        try {
            Constructor<?> constructor = CONSTRUCTOR_CACHE.get(type);
            if(constructor == null) {
                constructor = REFLECTION_FACTORY.newConstructorForSerialization( 
                        type, Object.class.getDeclaredConstructor( new Class[0] ) );
                constructor.setAccessible( true );
                CONSTRUCTOR_CACHE.put(type, constructor);
            }
            Object inst = constructor.newInstance( INITARGS );
            return (T) inst;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
             e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
           e.printStackTrace();
        } catch (InvocationTargetException e) {
			e.printStackTrace();
		}
        throw ex;
    }
    */
}
