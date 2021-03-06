
package com.vmware.vim25;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:07.446+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "EVCConfigFaultFault", targetNamespace = "urn:vim25")
public class EVCConfigFaultFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.vim25.EVCConfigFault evcConfigFaultFault;

    public EVCConfigFaultFaultMsg() {
        super();
    }
    
    public EVCConfigFaultFaultMsg(String message) {
        super(message);
    }
    
    public EVCConfigFaultFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public EVCConfigFaultFaultMsg(String message, com.vmware.vim25.EVCConfigFault evcConfigFaultFault) {
        super(message);
        this.evcConfigFaultFault = evcConfigFaultFault;
    }

    public EVCConfigFaultFaultMsg(String message, com.vmware.vim25.EVCConfigFault evcConfigFaultFault, Throwable cause) {
        super(message, cause);
        this.evcConfigFaultFault = evcConfigFaultFault;
    }

    public com.vmware.vim25.EVCConfigFault getFaultInfo() {
        return this.evcConfigFaultFault;
    }
}
