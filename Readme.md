LibreOffice Edit Online Alfresco Repository AMP module (V1)
========

This is an Alfresco Repository AMP module that adds WOPI host capabilities to Alfresco.<br/>
It does not implement the full WOPI protocol, merely the following endpoint interfaces:
- [ ] CheckFileInfo
- [ ] GetFile and 
- [ ] PutFile

This module was developed against Alfresco version 5.0.d.       
        
### Webapplication Open Platform Interface protocols
For information on WOPI, see http://wopi.readthedocs.io/projects/wopirest/en/latest/endpoints.html for the list of WOPI endpoints.

### Installing
First install LibreOfficeOnLine.
(See https://github.com/LibreOffice/online for the latest installation instructions).<br/>
See http://docs.alfresco.com/5.0/tasks/dev-extensions-tutorials-simple-module-install-amp.html for how to install this module in your Alfresco repository installation.<br/>
Add the following properties to your alfresco-global.properties file:
- [ ] lool.wopi.url=https://lool.server.domain:[port-number]
- [ ] lool.wopi.url.discovery=https://host.domain/path-to-discovery.xml
- [ ] lool.wopi.alfresco.host=https://alfresco.proxy-url-host.domain (Not important but used to get around proxy issues for now)

### JDK 8+

The page module evaluator requires JDK higher than or equal to version 8.<br/>
Go to http://www.oracle.com/technetwork/java/javase/downloads/index.html and click on button "Download JDK".<br/>
There are installation instructions on that page as well. To verify that your installation was successful, run "java -version" on the command line.<br/>
That should print the installed version of your JDK.

### Contributing

Submit pull a pull request. You're also welcome to fork the code for your own purpose(s).

### License

This code is released and distributed under the Mozilla Public License 2.0:
- [ ] https://opensource.org/licenses/MPL-2.0 
- [ ] https://tldrlegal.com/license/mozilla-public-license-2.0-%28mpl-2%29

### Reporting problems

Every self-respecting developer should have read link on how to ask smart questions: http://www.catb.org/~esr/faqs/smart-questions.html.

After you've done that you can create issues in https://github.com/magenta-aps/libreoffice-online-repo/issues.
      
### Features to consider

- [ ] Register edit action in audit log.
- [ ] implement option to lock editing to one user at a time.
