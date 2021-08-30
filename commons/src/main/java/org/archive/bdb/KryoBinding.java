/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.bdb;

import java.lang.ref.WeakReference;
import java.util.logging.Logger;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * Binding for use with BerkeleyDB-JE that uses Kryo serialization rather
 * than BDB's (custom version of) Java serialization.
 * 
 * @author gojomo
 */
public class KryoBinding<K> implements EntryBinding<K> {

    private static final Logger logger = Logger.getLogger(KryoBinding.class.getName());
    
    protected Class<K> baseClass;
    
    static private final ThreadLocal<AutoKryo> kryos = new ThreadLocal<AutoKryo>() {
	   protected AutoKryo initialValue() {
		   AutoKryo kryo = new AutoKryo();
	      return kryo;
	   };
	};
    	
    protected ThreadLocal<WeakReference<Output>> threadBuffer = new ThreadLocal<WeakReference<Output>>() {
        @Override
        protected WeakReference<Output> initialValue() {
        	return new WeakReference<Output>(new Output(16*1024, -1));
        }
    };
    
    /**
     * Constructor. Save parameters locally, as superclass 
     * fields are private. 
     * 
     * @param baseClass is the base class for serialized objects stored using
     * this binding
     */
    public KryoBinding(Class<K> baseClass) {
        this.baseClass = baseClass;
        // Register heavily-used classes: 
        kryos.get().register(baseClass);
        // Auto-register classes (n.b. this does not work safely across threads - see org.archive.bdb.AutoKryo):
        //kryos.get().autoregister(baseClass);
    }

    public Kryo getKryo() {
        return kryos.get();
    }
    
    private Output getBuffer() {
        WeakReference<Output> ref = threadBuffer.get();
        Output ob = ref.get();
        if (ob == null) {
            ob = new Output(16*1024, -1);
            threadBuffer.set(new WeakReference<Output>(ob));
        }
        ob.clear();
        return ob;
    }
    
    /**
     * Copies superclass simply to allow different source for FastOoutputStream.
     * 
     * @see com.sleepycat.bind.serial.SerialBinding#entryToObject
     */
    public void objectToEntry(K object, DatabaseEntry entry) {
    	Output output = getBuffer();
    	kryos.get().writeObject(output, object);
        entry.setData(output.toBytes());
    }

	@Override
    public K entryToObject(DatabaseEntry entry) {
		Input input = new Input(entry.getData());
		return (K) kryos.get().readObjectOrNull(input, baseClass);
    }
	
}
