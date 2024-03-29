/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.pdf;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Date;

import junit.framework.TestCase;

/**
 *
 * @author ltheussl
 */
public class DateBeanTest extends TestCase {
    /**
     * Test DateBean.
     */
    public void testDateBean() {
        DateBean date = new DateBean();
        date.setDate(new Date(0L));

        assertEquals("1970-01-01", date.getDate());
        assertEquals("1970-01-01T00:00:00Z", date.getDateTime());
        assertEquals("01", date.getDay());
        assertEquals("00", date.getHour());
        assertEquals("000", date.getMillisecond());
        assertEquals("00", date.getMinute());
        assertEquals("01", date.getMonth());
        assertEquals("00", date.getSecond());
        assertEquals("00:00:00Z", date.getTime());
        assertEquals("1970", date.getYear());
    }
}
