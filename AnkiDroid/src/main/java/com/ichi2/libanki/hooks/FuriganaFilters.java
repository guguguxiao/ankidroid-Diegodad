/***************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.libanki.hooks;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
// 永远在线的挂钩
public class FuriganaFilters {
    private static final Pattern r = Pattern.compile(" ?([^ >]+?)\\[(.+?)\\]");

    private static final String RUBY = "<ruby><rb>$1</rb><rt>$2</rt></ruby>";

    public void install(Hooks h) {
        h.addHook("fmod_kanji", new Kanji());
        h.addHook("fmod_kana", new Kana());
        h.addHook("fmod_furigana", new Furigana());
}


    private static String noSound(Matcher match, String repl) {
        if (match.group(2).startsWith("sound:")) {
            // return without modification
            return match.group(0);
        } else {
            return r.matcher(match.group(0)).replaceAll(repl);
        }
    }

    public class Kanji extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            Matcher m = r.matcher((String) arg);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, noSound(m, "$1"));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }

    public class Kana extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            Matcher m = r.matcher((String) arg);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, noSound(m, "$2"));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }

    public class Furigana extends Hook {
        @Override
        public Object runFilter(Object arg, Object... args) {
            Matcher m = r.matcher((String) arg);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, noSound(m, RUBY));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    }
}
