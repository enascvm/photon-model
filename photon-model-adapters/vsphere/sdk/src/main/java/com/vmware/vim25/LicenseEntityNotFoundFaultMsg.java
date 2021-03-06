
package com.vmware.vim25;

import javax.xml.ws.WebFault;


/**
 * This class was generated by Apache CXF 3.1.6
 * 2017-05-25T13:48:07.341+05:30
 * Generated source version: 3.1.6
 */

@WebFault(name = "LicenseEntityNotFoundFault", targetNamespace = "urn:vim25")
public class LicenseEntityNotFoundFaultMsg extends Exception {
    public static final long serialVersionUID = 1L;
    
    private com.vmware.vim25.LicenseEntityNotFound licenseEntityNotFoundFault;

    public LicenseEntityNotFoundFaultMsg() {
        super();
    }
    
    public LicenseEntityNotFoundFaultMsg(String message) {
        super(message);
    }
    
    public LicenseEntityNotFoundFaultMsg(String message, Throwable cause) {
        super(message, cause);
    }

    public LicenseEntityNotFoundFaultMsg(String message, com.vmware.vim25.LicenseEntityNotFound licenseEntityNotFoundFault) {
        super(message);
        this.licenseEntityNotFoundFault = licenseEntityNotFoundFault;
    }

    public LicenseEntityNotFoundFaultMsg(String message, com.vmware.vim25.LicenseEntityNotFound licenseEntityNotFoundFault, Throwable cause) {
        super(message, cause);
        this.licenseEntityNotFoundFault = licenseEntityNotFoundFault;
    }

    public com.vmware.vim25.LicenseEntityNotFound getFaultInfo() {
        return this.licenseEntityNotFoundFault;
    }
}
