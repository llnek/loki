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

/**
 * @author Kenneth Leung
 */
public interface Events {

  // Event types
  public static final long PROTECTED        = 1L;
  public static final long PUBLIC         = 2L;
  public static final long PRIVATE          = 3L;

  // Reply codes
  public static final long OK         = 200L;
  public static final long ERROR      = 500L;

  // request codes
  public static final long PLAYGAME_REQ       = 600L;
  public static final long PLAYREQ_OK         = 601L;
  public static final long PLAYREQ_NOK        = 602L;

  public static final long JOINGAME_REQ       = 651L;
  public static final long JOINREQ_OK         = 652L;
  public static final long JOINREQ_NOK        = 653L;

  // user and room codes
  public static final long USER_NOK           = 700L;
  public static final long GAME_NOK           = 701L;
  public static final long ROOM_NOK           = 702L;
  public static final long ROOM_FILLED        = 703L;
  public static final long ROOMS_FULL         = 704L;

  // msg codes
  public static final long PLAYER_JOINED      = 800L;
  public static final long AWAIT_START        = 801L;

  public static final long CONNECTED          = 820L;
  public static final long STARTED            = 821L;
  public static final long CLOSED             = 822L;

  public static final long RESTART            = 840L;
  public static final long START              = 841L;
  public static final long STOP               = 842L;
  public static final long START_ROUND        = 843L;
  public static final long END_ROUND          = 844L;

  public static final long POKE_RUMBLE        = 860L;
  public static final long POKE_MOVE          = 861L;
  public static final long POKE_WAIT          = 862L;
  public static final long SYNC_ARENA         = 863L;

  public static final long REPLAY             = 880L;
  public static final long PLAY_MOVE          = 881L;
  public static final long GAME_WON          = 890L;
  public static final long GAME_TIE          = 891L;

  // end game
  public static final long QUIT          = 911L;

  // session status
  public static final long S_NOT_CONNECTED    = 0L;
  public static final long S_CONNECTED        = 1L;


}

