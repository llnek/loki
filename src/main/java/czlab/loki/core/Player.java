/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */

package czlab.loki.core;

import czlab.xlib.Identifiable;
import czlab.xlib.Nameable;

/**
 * @author Kenneth Leung
 */
public interface Player extends Nameable, Identifiable {

  /**
   */
  public void removeSession(Session s);

  /**
   */
  public void addSession(Session s);

  /**
   */
  public int countSessions();

  /**
   */
  public void setEmailId(String emailId);

  /**
   */
  public String emailId();

  /**
   */
  public void setName(String name);

  /**
   */
  public void logout();


}
