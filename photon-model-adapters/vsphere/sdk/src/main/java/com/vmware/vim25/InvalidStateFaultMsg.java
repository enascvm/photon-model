
package com.vmware.vim25;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:06.705+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "InvalidStateFault", targetNamespace = "urn:vim25")
public class InvalidStateFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.vim25.InvalidState invalidStateFault;

    public InvalidStateFaultMsg() {
        super();
    }
    
    public InvalidStateFaultMsg(String message) {
        super(message);
    }
    
    public InvalidStateFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidStateFaultMsg(String message, com.vmware.vim25.InvalidState invalidStateFault) {
        super(message);
        this.invalidStateFault = invalidStateFault;
    }

    public InvalidStateFaultMsg(String message, com.vmware.vim25.InvalidState invalidStateFault, Throwable cause) {
        super(message, cause);
        this.invalidStateFault = invalidStateFault;
    }

    public com.vmware.vim25.InvalidState getFaultInfo() {
        return this.invalidStateFault;
    }
}
