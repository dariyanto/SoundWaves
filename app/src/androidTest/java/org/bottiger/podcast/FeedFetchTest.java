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


import org.bottiger.podcast.service.PodcastService;

public class FeedFetchTest extends android.test.ServiceTestCase<PodcastService> {

	PodcastService PodcastServiceInstance;
    public FeedFetchTest() {
		super(PodcastService.class);
	}
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        /*
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), PodcastService.class);
        startService(startIntent); 
        while(PodcastServiceInstance==null){
        	PodcastServiceInstance = getService();
        }
        */
    }
 /*
    
	public void testFetchNetworkErr() throws Exception {
    	String url = "http://www.xxx.com/aaa/bbb";
    	FeedParserListener handler = PodcastServiceInstance.fetchFeed(url);
    	int code = handler.resultCode;
        assertTrue(code==R.string.network_fail);

    }

	public void testFetchFormatErr() throws Exception {
    	String url = "http://www.baidu.com";
    	FeedParserListener handler = PodcastServiceInstance.fetchFeed(url);
    	int code = handler.resultCode;
        assertTrue(code==R.string.feed_format_error);

    }	
	
	public void testFetchOK() throws Exception {
    	String url = "http://podcast.rthk.org.hk/podcast/observeworld.xml";
    	FeedParserListener handler = PodcastServiceInstance.fetchFeed(url);
    	int code = handler.resultCode;
    	assertTrue(code==0);

    }	
*/    
  
}
