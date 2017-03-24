/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.loki.net;

import java.util.HashMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * @author Kenneth Leung
 */
public enum Msgs {

  PROTECTED(1),
  PUBLIC(2),
  PRIVATE(3),

  // Reply codes
  OK(200),
  ERROR(500),

  // request codes
  PLAYGAME_REQ(600),
  PLAYREQ_OK(601),
  PLAYREQ_NOK(602),

  JOINGAME_REQ(651),
  JOINREQ_OK(652),
  JOINREQ_NOK(653),

  // user and room codes
  USER_NOK(700),
  GAME_NOK(701),
  ROOM_NOK(702),
  ROOM_FILLED(703),
  ROOMS_FULL(704),

  // msg codes
  PLAYER_JOINED(800),
  AWAIT_START(801),

  CONNECTED(820),
  STARTED(821),
  CLOSED(822),

  RESTART(840),
  START(841),
  STOP(842),
  START_ROUND(843),
  END_ROUND(844),

  POKE_RUMBLE(860),
  POKE_MOVE(861),
  POKE_WAIT(862),
  SYNC_ARENA(863),

  REPLAY(880),
  PLAY_MOVE(881),
  GAME_WON(890),
  GAME_TIE(891),

  // end game
  QUIT(911);


  /**
   */
  public int value() { return _value; }

  /**
   */
  public static Msgs get(int v) {
    return _lookup.get(v);
  }

  /**
   */
  private Msgs(int v) {
    _value=v;
  }


  private static final Map<Integer,Msgs> _lookup = new HashMap<>();
  private int _value;

  static {
    for (Msgs s : EnumSet.allOf(Msgs.class))
    _lookup.put(s.value(), s);
  }

}

