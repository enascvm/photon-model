
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.MethodFault;


/**
 * <p>Java class for PbmFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}MethodFault"&gt;
 *       &lt;sequence&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmFault")
@XmlSeeAlso({
    PbmAlreadyExists.class,
    PbmDuplicateName.class,
    PbmFaultInvalidLogin.class,
    PbmLegacyHubsNotSupported.class,
    PbmNonExistentHubs.class,
    PbmFaultNotFound.class,
    PbmFaultProfileStorageFault.class,
    PbmCompatibilityCheckFault.class,
    PbmResourceInUse.class
})
public class PbmFault
    extends MethodFault
{


}
