/*
 * Brainstorm - THT utility from Thought Networks
 * 
 * Copyright (c) 2018 - 2020, Thought Network LLC
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as 
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */
package live.thought.brainstorm;

import org.fusesource.jansi.AnsiConsole;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.fusesource.jansi.Ansi;

public abstract class Console
{
  protected static int debugLevel;
  protected static final SimpleDateFormat sdf = new SimpleDateFormat("[YYYY-MM-dd HH:mm:ss] ");
  
  static {
    AnsiConsole.systemInstall();   
  }
  
  public static int getLevel()
  {
    return debugLevel;
  }
  
  public static void setLevel(int level)
  {
    debugLevel = level;
  }
  
  public static void print(Object content)
  {
    System.out.print( Ansi.ansi().render(content.toString()));
  }
  
  public static void println(Object content)
  {
    System.out.println( Ansi.ansi().render(content.toString()));
  }
  
  public static void output(Object content)
  {
    Console.println(sdf.format(new Date()) + content.toString());
  }
  
  public static void debug(Object content, int level)
  {
    if (level <= debugLevel)
    {
      Console.output("@|faint,white " + content.toString() + "|@");
    }
  }
  
  public static void end()
  {
    AnsiConsole.systemUninstall();
  }
  
}
