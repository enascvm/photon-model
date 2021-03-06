
package com.vmware.vim25;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:06.995+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "InsufficientResourcesFaultFault", targetNamespace = "urn:vim25")
public class InsufficientResourcesFaultFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.vim25.InsufficientResourcesFault insufficientResourcesFaultFault;

    public InsufficientResourcesFaultFaultMsg() {
        super();
    }
    
    public InsufficientResourcesFaultFaultMsg(String message) {
        super(message);
    }
    
    public InsufficientResourcesFaultFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public InsufficientResourcesFaultFaultMsg(String message, com.vmware.vim25.InsufficientResourcesFault insufficientResourcesFaultFault) {
        super(message);
        this.insufficientResourcesFaultFault = insufficientResourcesFaultFault;
    }

    public InsufficientResourcesFaultFaultMsg(String message, com.vmware.vim25.InsufficientResourcesFault insufficientResourcesFaultFault, Throwable cause) {
        super(message, cause);
        this.insufficientResourcesFaultFault = insufficientResourcesFaultFault;
    }

    public com.vmware.vim25.InsufficientResourcesFault getFaultInfo() {
        return this.insufficientResourcesFaultFault;
    }
}
