import java.util.UUID;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.File;
import java.util.List;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

String return1="";
String return2="";
String return3="";
String name = "";

if (ServletFileUpload.isMultipartContent(request)){
    ServletFileUpload uploadHandler = new ServletFileUpload(new DiskFileItemFactory());
    //UTF-8 encoding set
    uploadHandler.setHeaderEncoding("UTF-8");
    List items = uploadHandler.parseRequest(request);
    //each feild tag foreach assert that
    for (FileItem item : items) {
        if(item.getFieldName().equals("callback")) {
            return1 = item.getString("UTF-8");
        } else if(item.getFieldName().equals("callback_func")) {
            return2 = "?callback_func="+item.getString("UTF-8");
        } else if(item.getFieldName().equals("Filedata")) {
            //FILE tag one more
            if(item.getSize() > 0) {
                String ext = item.getName().substring(item.getName().lastIndexOf(".")+1);
                //file default path
                String defaultPath = request.getServletContext().getRealPath("/");
                //file ext path 
                String path = defaultPath + "upload" + File.separator;
                 
                File file = new File(path);
                 
                //dir chk
                if(!file.exists()) {
                    file.mkdirs();
                }
                //upload filename
                String realname = UUID.randomUUID().toString() + "." + ext;
                ///////////////// file write /////////////////
                InputStream is = item.getInputStream();
                OutputStream os=new FileOutputStream(path + realname);
                int numRead;
                byte b[] = new byte[(int)item.getSize()];
                while((numRead = is.read(b,0,b.length)) != -1){
                    os.write(b,0,numRead);
                }
                if(is != null)  is.close();
                os.flush();
                os.close();
                /////////////////file write /////////////////
                return3 += "&bNewLine=true&sFileName="+name+"&sFileURL=/upload/"+realname;
            }else {
                return3 += "&errstr=error";
            }
        }
    }
}
 response.sendRedirect(return1+return2+return3);

