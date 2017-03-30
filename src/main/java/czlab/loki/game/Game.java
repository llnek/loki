/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.loki.game;

import czlab.jasal.Restartable;
import czlab.jasal.Startable;
import czlab.jasal.Initable;

/**
 * @author Kenneth Leung
 */
public interface Game extends Initable, Startable, Restartable {

  /**
   */
  public void startRound(Object arg);

  /**
   */
  public Object playerGist(Object id);

  /**
   */
  public void endRound();

  /**
   */
  public Object onEvent(Object evt);

}


