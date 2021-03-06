/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bottiger.podcast;

import junit.framework.TestCase;

import org.bottiger.podcast.provider.Subscription;

import java.util.Arrays;
import java.util.List;

/**
 * An example of a true unit test that tests the utility class {@link MorseCodeConverter}.
 * Since this test doesn't need a {@link android.content.Context}, or any other
 * dependencies injected, it simply extends the standard {@link TestCase}.
 *
 * See {@link com.example.android.apis.AllTests} for documentation on running
 * all tests and individual tests in this application.
 */
public class SubsTest extends TestCase {

    /**
     * Problematic feeds from the crashlogs:
     *
     * 1) http://www.andymoor.com/ is wonky
     * 2) http://streaming.radio24syv.dk/pls/24syv_64_IR.pls
     */
    private List<String> mProblematicFeeds = Arrays.asList("http://downloads.bbc.co.uk/podcasts/worldservice/wbnews/rss.xml");

    public void testSubsNew() throws Exception {
    	
    	Subscription subs = new Subscription();
        //assertEquals("error", null, subs);

		
        assertTrue(subs.id==-1);
        assertTrue(subs.title==null);
        assertTrue(subs.url==null);
        assertTrue(subs.description==null);
        assertTrue(subs.link==null);
        assertTrue(subs.comment=="");        
        assertTrue(subs.lastUpdated==-1);
        assertTrue(subs.fail_count==-1);
        assertTrue(subs.lastItemUpdated==-1);
        assertTrue(subs.auto_download==-1);        
    }

    public void testSubsNew2() throws Exception {
    	
    	String url = "http://www.xxx.com/aaa/bbb";
    	Subscription subs = new Subscription(url);
        assertTrue(subs.id==-1);
        assertTrue(subs.title==url);
        assertTrue(subs.url==url);
        assertTrue(subs.description==null);
        assertTrue(subs.link==url);
        assertTrue(subs.comment=="");          
        assertTrue(subs.lastUpdated==-1);
        assertTrue(subs.fail_count==-1);
        assertTrue(subs.lastItemUpdated==-1);
        assertTrue(subs.auto_download==-1);   
    }    
}
