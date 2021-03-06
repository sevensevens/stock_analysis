/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package yahooscraper;

import hibernate.*;
import hibernate.entities.Stock;
import hibernate.entities.StockEntry;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author gtri
 */
public class LoadFromYahoo 
{
    static final Logger logger = LoggerFactory.getLogger(LoadFromYahoo.class);

    public static void loadInAllStocksFromNASDAQFile()
    {
        try
        {
            HibernateUtil.startNewSession();
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File("nasdaq.csv"))));
            String line;
            while((line = in.readLine()) != null)
            {
                String arr[] = line.split(";");
                String symbol = arr[0];
                symbol = symbol.replaceAll("\"", "");

                if(Stock.isInDB(symbol) == false)
                {
                        HibernateUtil.beginTransaction();
                        Stock stock = new Stock();
                        stock.setSymbol(symbol);
                        HibernateUtil.getCurrentSession().save(stock);
                        logger.info("Added " + symbol + " to database");
                        HibernateUtil.commit();
                }				
            }
            HibernateUtil.closeSession();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void updateAllStocksFromYahoo()
    {
        HibernateUtil.startNewSession();
        List<Stock> stocks = Stock.getAllStocks();		
        int count = 0, max = stocks.size();

        // get the symbol from each stock
        // we can't keep references to all these objects
        // or hibernate will causes a heap space overflow
        List<String> stockNames = new ArrayList<String>();		
        for(Stock stock : stocks)
        {
            stockNames.add(stock.getSymbol());
        }		
        HibernateUtil.closeSession();

        // iterate through each stock
        for(String stockname : stockNames)
        {
            // get the stock
            HibernateUtil.startNewSession();
            Stock stock = Stock.findStock(stockname);

            // grab the data from yahoo and save it
            HibernateUtil.beginTransaction();
            HibernateUtil.getCurrentSession().update(stock);
            loadStockFromYahoo(stock, new org.joda.time.DateTime(1970, 1, 1, 0, 0));
            HibernateUtil.getCurrentSession().save(stock);
            HibernateUtil.commit();

            // use evict to ensure that hibernate is not holding onto
            // references for saved objects
            for(StockEntry se : stock.getEntries())
            {
                    HibernateUtil.getCurrentSession().evict(se);				
            }
            HibernateUtil.getCurrentSession().evict(stock);			

            // log which stock we just updated/added
            count++;				
            logger.info("Updating " + stock.toString() + "\t	" + count + "/"+max);				
            HibernateUtil.closeSession();
        }
    }

    public static void loadStockFromYahoo(Stock stock, org.joda.time.DateTime from)
    {		
        loadStockFromYahoo(stock, from, org.joda.time.LocalDate.now().toDateTimeAtStartOfDay());
    }

    public static void loadStockFromYahoo(Stock stock, org.joda.time.DateTime from, org.joda.time.DateTime to)
    {
        org.joda.time.DateTime today = org.joda.time.LocalDate.now().toDateTimeAtStartOfDay();
        org.joda.time.DateTime mostrecentday = stock.getMostRecentEntry();
        if(mostrecentday == null)
        {
                mostrecentday = from;
        }

        if(!mostrecentday.equals(today))
        {
            List<StockEntry> entries = stock.getEntries();

            List<StockEntry> newdata = getHistoricalData(stock.getSymbol(), from, today);
            if(newdata != null)
            {
                    for(StockEntry se : newdata)
                    {
                            entries.add(se);
                    }
                    stock.setEntries(entries);
            }
        }
    }

    protected static List<StockEntry> getHistoricalData(String symbol, org.joda.time.DateTime start, org.joda.time.DateTime end)
    {
        int DATE_INDEX=0, OPEN_INDEX=1, HIGH_INDEX=2, LOW_INDEX=3, CLOSE_INDEX=4, VOLUMNE_INDEX=5, ADJ_CLOSE_INDEX=6;
        int a = start.getMonthOfYear(), b = start.getDayOfMonth(), c = start.getYear();
        int d = end.getMonthOfYear(), e = end.getDayOfMonth(), f = end.getYear();

        try
        {
                                            //   http://ichart.finance.yahoo.com/table.csv?s=FLWS&a=10&b=20&c=2011&d=10&e=30&f=2011&g=d&ignore=.csv							
                                            //   http://ichart.finance.yahoo.com/table.csv?s=MSFT&a=01&b=01&c=1901&d=08&e=06&f=111&g=d&ignore=.csv
                                            //   http://ichart.finance.yahoo.com/table.csv?s=GOOG&a=03&b=12&c=1999&d=09&e=30&f=2011&g=d&ignore=.csv
            URL yahoo = new URL("http://ichart.finance.yahoo.com/table.csv?s="+symbol+	"&a=" + String.format("%02d", a) +
                                                                                                                                                                    "&g=d&ignore=.csv");
            URLConnection yahooconn = yahoo.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(yahooconn.getInputStream()));
            ArrayList<StockEntry> ret= new ArrayList<StockEntry>();
            in.readLine();
            String line;
            while((line = in.readLine()) != null)
            {
                String arr[] = line.split(",");
                StockEntry se = new StockEntry();
                se.setDate(parseDate(arr[DATE_INDEX]));
                se.setOpen(parseDoubleValue(arr[OPEN_INDEX]));
                se.setHigh(parseDoubleValue(arr[HIGH_INDEX]));
                se.setLow(parseDoubleValue(arr[LOW_INDEX]));
                se.setClose(parseDoubleValue(arr[CLOSE_INDEX]));
                se.setVolume(parseIntValue(arr[VOLUMNE_INDEX]));
                ret.add(se);
            }

            return ret;
        }
        catch(Exception ex)
        {
                logger.warn("Cannot load historical data for " + symbol);
                return null;						
        }
    }	

    protected static org.joda.time.DateTime parseDate(String sdate)
    {
        return org.joda.time.LocalDate.parse(sdate).toDateTimeAtStartOfDay();
    }

    protected static double parseDoubleValue(String val)
    {
        try
        {
            return Double.parseDouble(val);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return 0;
        }
    }

    protected static int parseIntValue(String val)
    {
        try
        {
            return Integer.parseInt(val);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return 0;
        }
    }	
}
