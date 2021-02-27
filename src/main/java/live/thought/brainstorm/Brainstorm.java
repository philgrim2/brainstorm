package live.thought.brainstorm;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import live.thought.thought4j.ThoughtClientInterface.BasicTxOutput;
import live.thought.thought4j.ThoughtClientInterface.MasternodeOutput;
import live.thought.thought4j.ThoughtClientInterface.TxInput;
import live.thought.thought4j.ThoughtClientInterface.TxOutput;
import live.thought.thought4j.ThoughtClientInterface.Unspent;
import live.thought.thought4j.ThoughtRPCClient;

public class Brainstorm
{
  /** RELEASE VERSION */
  public static final String               VERSION          = "v0.1";
  /** Options for the command line parser. */
  protected static final Options           options          = new Options();
  /** The Commons CLI command line parser. */
  protected static final CommandLineParser gnuParser        = new GnuParser();
  /** Default values for connection. */
  private static final String              DEFAULT_HOST     = "localhost";
  private static final String              DEFAULT_PORT     = "10617";
  private static final String              DEFAULT_USER     = "user";
  private static final String              DEFAULT_PASS     = "password";
  private static final String              DEFAULT_INTERVAL = Integer.toString(60 * 60 * 2);     // 2 hours
  private static final String              DEFAULT_TRANSFER = Integer.toString(100);
  private static final String              DEFAULT_MINB     = Double.toString(315000.0); // Stay above masternode stake by default

  private static final String              HOST_PROPERTY    = "host";
  private static final String              PORT_PROPERTY    = "port";
  private static final String              USER_PROPERTY    = "user";
  private static final String              PASS_PROPERTY    = "password";
  private static final String              ADDR_PROPERTY    = "addr";
  private static final String              INTR_PROPERTY    = "interval";
  private static final String              TXFR_PROPERTY    = "transfer";
  private static final String              MINB_PROPERTY    = "minimum";
  private static final String              HELP_OPTION      = "help";
  private static final String              CONFIG_OPTION    = "config"; 

  /** Connection for Thought daemon */
  private ThoughtRPCClient                 client;

  String                                   targetAddress;
  int                                      interval;
  int                                      transfer;
  double                                   minBalance;
  
  /** Set up command line options. */
  static
  {
    options.addOption("h", HOST_PROPERTY, true, "Thought RPC server host (default: localhost)");
    options.addOption("P", PORT_PROPERTY, true, "Thought RPC server port (default: 10617)");
    options.addOption("u", USER_PROPERTY, true, "Thought server RPC user");
    options.addOption("p", PASS_PROPERTY, true, "Thought server RPC password");
    options.addOption("a", ADDR_PROPERTY, true, "Thought wallet address to send coins to");
    options.addOption("i", INTR_PROPERTY, true, "Polling interval in seconds (default: 2 hours)");
    options.addOption("t", TXFR_PROPERTY, true, "Threshhold amount to transfer per interval (default: 100)");
    options.addOption("m", MINB_PROPERTY, true, "Minimum balance to keep in the source account. (Defaults to 315,000.0)");
    options.addOption("H", HELP_OPTION, true, "Displays usage information");
    options.addOption("f", CONFIG_OPTION, true,
        "Configuration file to load options from.  Command line options override config file.");
  }

  public Brainstorm(Properties props)
  {
    String host = props.getProperty(HOST_PROPERTY, DEFAULT_HOST);
    int    port = Integer.parseInt(props.getProperty(PORT_PROPERTY, DEFAULT_PORT));
    String user = props.getProperty(USER_PROPERTY, DEFAULT_USER);
    String pass = props.getProperty(PASS_PROPERTY, DEFAULT_PASS);
    targetAddress = props.getProperty(ADDR_PROPERTY);
    interval = Integer.parseInt(props.getProperty(INTR_PROPERTY, DEFAULT_INTERVAL));
    transfer = Integer.parseInt(props.getProperty(TXFR_PROPERTY, DEFAULT_TRANSFER));
    minBalance = Double.parseDouble(props.getProperty(MINB_PROPERTY, DEFAULT_MINB));

    URL url = null;
    try
    {
      url = new URL("http://" + user + ':' + pass + "@" + host + ":" + port + "/");
      client = new ThoughtRPCClient(url);
    }
    catch (MalformedURLException e)
    {
      throw new IllegalArgumentException("Invalid URL: " + url);
    }
  }

  private boolean checkCanSpend(Unspent unspent, List<MasternodeOutput> outputs)
  {
    boolean retval = true;
    
    for (MasternodeOutput o: outputs)
    {
      if (o.txid() == unspent.txid())
      {
        retval = false;
        break;
      }
    }
    
    return retval;
  }
  
  public void run()
  {
    boolean moreElectricity = true;
    List<MasternodeOutput> verboten = null;
    try
    {
      Console.output("Checking for reserved masternode transaction.");
      verboten = client.masternodeOutputs();
    }
    catch (Exception e)
    {
      Console.output("Not a masternode - no reserved transaction.");
      verboten = new ArrayList<MasternodeOutput>();
    }

    while (moreElectricity)
    {
      double balance = client.getBalance();
      Console.output("Current balance: " + balance);
      if (balance > minBalance)
      {
        Console.output("Looking for inputs.");
        List<Unspent> unspent = client.listUnspent(6);
        if (null != unspent && unspent.size() > 0)
        {
          List<TxInput> inputs = new ArrayList<TxInput>();
          double running = 0.0;
          for (Unspent tx : unspent)
          {
            if (checkCanSpend(tx, verboten))
            {
              inputs.add(tx);
              running += tx.amount();
            }
            if (running >= transfer)
            {
              Console.output("Found enough inputs.");
              BasicTxOutput output = new BasicTxOutput(targetAddress, running);
              List<TxOutput> outputs = new ArrayList<TxOutput>();
              outputs.add(output);
              String rawtx = client.createRawTransaction(inputs, outputs);
              client.sendRawTransaction(rawtx);
              Console.output("@|green Transaction sent.|@");
              break;
            }
          }
          
        }
      }
      else
      {
        Console.output("Account balance at or below minumum.  No transfer this cycle.");
      }
      try
      {
        Thread.sleep(interval * 1000);
      }
      catch (InterruptedException e)
      {
        Console.output("Who has disturbed my slumber?");
      }
    }
  }

  protected static void usage()
  {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("Brainstorm", options);
  }

  public static void main(String[] args)
  {

    CommandLine commandLine = null;

    try
    {
      Properties props = new Properties();
      // Read the command line
      commandLine = gnuParser.parse(options, args);
      // Check for the help option
      if (commandLine.hasOption(HELP_OPTION))
      {
        usage();
        System.exit(0);
      }
      // Check for a config file specified on the command line
      if (commandLine.hasOption(CONFIG_OPTION))
      {
        try
        {
          props.load(new FileInputStream(new File(commandLine.getOptionValue(CONFIG_OPTION))));
        }
        catch (Exception e)
        {
          Console.output(String.format("@|red Specified configuration file %s unreadable or not found.|@",
              commandLine.getOptionValue(CONFIG_OPTION)));
          System.exit(1);
        }
      }
      // Command line options override config file values
      if (commandLine.hasOption(HOST_PROPERTY))
      {
        props.setProperty(HOST_PROPERTY, commandLine.getOptionValue(HOST_PROPERTY));
      }
      if (commandLine.hasOption(PORT_PROPERTY))
      {
        props.setProperty(PORT_PROPERTY, commandLine.getOptionValue(PORT_PROPERTY));
      }
      if (commandLine.hasOption(USER_PROPERTY))
      {
        props.setProperty(USER_PROPERTY, commandLine.getOptionValue(USER_PROPERTY));
      }
      if (commandLine.hasOption(PASS_PROPERTY))
      {
        props.setProperty(PASS_PROPERTY, commandLine.getOptionValue(PASS_PROPERTY));
      }
      if (commandLine.hasOption(ADDR_PROPERTY))
      {
        props.setProperty(ADDR_PROPERTY, commandLine.getOptionValue(ADDR_PROPERTY));
      }
      if (commandLine.hasOption(INTR_PROPERTY))
      {
        props.setProperty(INTR_PROPERTY, commandLine.getOptionValue(INTR_PROPERTY));
      }
      if (commandLine.hasOption(TXFR_PROPERTY))
      {
        props.setProperty(TXFR_PROPERTY, commandLine.getOptionValue(TXFR_PROPERTY));
      }
      if (commandLine.hasOption(MINB_PROPERTY))
      {
        props.setProperty(MINB_PROPERTY, commandLine.getOptionValue(MINB_PROPERTY));
      }
      String address = props.getProperty(ADDR_PROPERTY);
      if (null == address)
      {
        Console.output("@|red No Thought target address specified.|@");
        usage();
        System.exit(1);
      }

      Brainstorm bs = new Brainstorm(props);
      bs.run();
      Console.end();
    }
    catch (ParseException pe)
    {
      System.err.println(pe.getLocalizedMessage());
      usage();
    }
    catch (Exception e)
    {
      System.err.println(e.getLocalizedMessage());
    }
  }
}
