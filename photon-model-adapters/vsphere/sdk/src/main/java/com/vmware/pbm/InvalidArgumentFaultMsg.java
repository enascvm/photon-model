
package com.vmware.pbm;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:23.442+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "InvalidArgumentFault", targetNamespace = "urn:pbm")
public class InvalidArgumentFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.vim25.InvalidArgument invalidArgumentFault;

    public InvalidArgumentFaultMsg() {
        super();
    }
    
    public InvalidArgumentFaultMsg(String message) {
        super(message);
    }
    
    public InvalidArgumentFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidArgumentFaultMsg(String message, com.vmware.vim25.InvalidArgument invalidArgumentFault) {
        super(message);
        this.invalidArgumentFault = invalidArgumentFault;
    }

    public InvalidArgumentFaultMsg(String message, com.vmware.vim25.InvalidArgument invalidArgumentFault, Throwable cause) {
        super(message, cause);
        this.invalidArgumentFault = invalidArgumentFault;
    }

    public com.vmware.vim25.InvalidArgument getFaultInfo() {
        return this.invalidArgumentFault;
    }
}