
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OvfSystemFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OvfSystemFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}OvfFault"&gt;
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
@XmlType(name = "OvfSystemFault")
@XmlSeeAlso({
    OvfDiskMappingNotFound.class,
    OvfHostValueNotParsed.class,
    OvfInternalError.class,
    OvfToXmlUnsupportedElement.class,
    OvfUnknownDevice.class,
    OvfUnknownEntity.class,
    OvfUnsupportedDeviceBackingInfo.class,
    OvfUnsupportedDeviceBackingOption.class
})
public class OvfSystemFault
    extends OvfFault
{


}
