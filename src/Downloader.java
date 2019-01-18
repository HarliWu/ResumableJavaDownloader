import java.text.SimpleDateFormat;
import java.util.*;
import java.io.*;
import java.net.*;

class Downloader {
    private URL url;
    private int contentLength;
    private String FileName;
    private String lastModify;
    private int downloaded;
    private boolean support;
    private boolean goon;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    Downloader(String url) {
        try {
            initial(new URL(url));
        } catch (Exception e) {
            File file = new File(url);
            if (url.substring(url.lastIndexOf(".") + 1).equals("downloading") && file.exists() && file.isFile()) {
                try {
                    initial(file);
                    return;
                } catch (Exception ignored) {
                }
            }
            System.out.println("The address is invalid. ");
            System.exit(-1);
        }
    }

    private void initial(URL url) throws IOException {
        this.url = url;
        String fName = url.toString().trim();
        FileName = fName.substring(fName.lastIndexOf("/") + 1);
        downloaded = -1;
        goon = false;
        support = SupportRange();
        downloaded = 0;
    }

    private void initial(File file) throws FileNotFoundException {
        InputStreamReader reader = new InputStreamReader(new FileInputStream(file));
        BufferedReader br = new BufferedReader(reader);
        try {
            url = new URL(br.readLine());
            contentLength = Integer.parseInt(br.readLine());
            FileName = br.readLine();
            lastModify = br.readLine();
            downloaded = Integer.parseInt(br.readLine());
            support = Boolean.parseBoolean(br.readLine());
            br.close();
            reader.close();
            file.delete();
            File f = new File("./" + FileName);
            if (f.exists() && f.isFile()) goon = true;
            else {
                initial(url);
            }
        } catch (IOException e) {
            System.out.println("The file has been damaged. ");
            System.exit(-1);
        }
    }

    private boolean SupportRange() throws IOException {
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("Group", "9");
        connection.setRequestProperty("connection", "Keep-Alive");
        connection.setRequestProperty("accept", "*/*");
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        while (true) {
            try {
                connection.connect();
                break;
            } catch (Exception e) {
                waitResponse();
            }
        }
        lastModify = connection.getHeaderField("Last-Modified");
        contentLength = connection.getContentLength();
        if (connection.getHeaderField("Accept-Ranges") == null) {
            return false;
        }
        return connection.getHeaderField("Accept-Ranges").equals("bytes");
    }

    private void waitResponse() throws IOException {
        if(downloaded==-1){
            System.out.println("[" + sdf.format(new Date()) + "] Cannot connect to the server. Please wait. ");
        }
        else {
            if(contentLength<=0){
                System.out.println("[" + sdf.format(new Date()) + "] Cannot connect to the server. Please wait. " +
                        "It has downloaded " + downloaded + " bytes. " );
            }
            else{
                double p = downloaded * 100.0 / contentLength;
                System.out.println("[" + sdf.format(new Date()) + "] Cannot connect to the server. Please wait. " +
                        "It has downloaded " + downloaded + " bytes (" + p + "%). " );
            }
        }
        File temp = new File("./" + (new Date()).getTime() + ".downloading");
        if (support) {
            //This file only available for supporting resumable
            temp.createNewFile();
            BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
            bw.write(url.toString() + "\r\n");
            bw.write(contentLength + "\r\n");
            bw.write(FileName + "\r\n");
            bw.write(lastModify + "\r\n");
            bw.write(downloaded + "\r\n");
            bw.write(support + "\r\n");
            bw.flush();
            bw.close();
        }
        while (true) {
            URLConnection connection = url.openConnection();
            connection.setRequestProperty("Group", "9");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("accept", "*/*");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            try {
                connection.connect();
                System.out.println("[" + sdf.format(new Date()) + "] Successfully connected.  ");
                if (support) temp.delete();
                return;
            } catch (Exception ignore) {
            }
        }
    }

    private int writeToFile(URLConnection connection, FileOutputStream fos) throws IOException{
        int tot = 0;
        try {
            InputStream is = connection.getInputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                tot += len;
                fos.write(buffer, 0, len);
            }
            return tot;
        } catch (Exception e) {
            return tot; //cannot completely write
        }
    }

    private int code206(URLConnection connection, FileOutputStream fos) throws IOException {
        if ((lastModify == null && connection.getHeaderField("Last-Modified") == null) ||
                connection.getHeaderField("Last-Modified").equals(lastModify)) {
            return writeToFile(connection, fos);
        }
        return -1; //last modified
    }

    private int downloadingTime = 0;

    void download() throws IOException {
        downloadingTime++;
        if(contentLength==-1)  System.out.println("[" + sdf.format(new Date()) + "] Download starts. The total download length is unknown. ");
        else{
            if(downloaded>0){
                System.out.println("[" + sdf.format(new Date()) + "] Download starts. The total download length is " + contentLength + " bytes. " +
                    "It has already downloaded " + downloaded + " bytes. ");
            }
            else{
                System.out.println("[" + sdf.format(new Date()) + "] Download starts. The total download length is " + contentLength + " bytes. ");
            }
        }
        File file = new File("./" + FileName);
        FileOutputStream fos = new FileOutputStream(file, goon);
        int request = 0;
        while (contentLength==-1 || downloaded < contentLength) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Group", "9");
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("accept", "*/*");
            if (support) {
                String Range = "bytes=" + downloaded + "-";
                connection.setRequestProperty("Range", Range);
            }
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            try {
                request++;
                connection.connect();
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    fos.close();
                    fos = new FileOutputStream(file);
                    int length = connection.getContentLength();
                    downloaded = writeToFile(connection, fos);
                    if(contentLength==-1) break;
                    request = 0;
                } else if (code == HttpURLConnection.HTTP_PARTIAL) {
                    int value = code206(connection, fos);
                    if (value >= 0) {
                        downloaded = downloaded + value;
                    } else {
                        System.out.println("[" + sdf.format(new Date()) + "] The downloaded file has been changed on " + connection.getHeaderField("Last-Modified") + ". Redownload is required. ");
                        lastModify=connection.getHeaderField("Last-Modified");
                        downloaded = 0;
                        fos.close();
                        fos = new FileOutputStream(file);
                    }
                    request = 0;
                } else {
                    if(contentLength==-1) break;
                    //this request is not valid
                    if (request == 5) {
                        //the request has been sent for five times
                        //redownload is required
                        if (downloadingTime > 5) {
                            System.out.println("[" + sdf.format(new Date()) + "] Download error. The download process will stop because the times of redownloading is exceeded. ");
                            fos.close();
                            file.delete();
                            return;
                        } else {
                            System.out.println("[" + sdf.format(new Date()) + "] Download error. Waiting for redownload again. ");
                            fos.close();
                            file.delete();
                            Thread.sleep(downloadingTime * 1000);
                            support = SupportRange();
                            downloaded = 0;
                            goon = false;
                            download();
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                waitResponse();
            }
        }
        fos.flush();
        fos.close();
        System.out.println("[" + sdf.format(new Date()) + "] Download finishes. ");
    }
}
