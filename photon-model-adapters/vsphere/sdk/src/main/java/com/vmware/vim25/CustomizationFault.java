
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CustomizationFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CustomizationFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
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
@XmlType(name = "CustomizationFault")
@XmlSeeAlso({
    CannotDecryptPasswords.class,
    CustomizationPending.class,
    IpHostnameGeneratorError.class,
    LinuxVolumeNotClean.class,
    MissingLinuxCustResources.class,
    MissingWindowsCustResources.class,
    MountError.class,
    NicSettingMismatch.class,
    NoDisksToCustomize.class,
    UncustomizableGuest.class,
    UnexpectedCustomizationFault.class,
    VolumeEditorError.class
})
public class CustomizationFault
    extends VimFault
{


}
