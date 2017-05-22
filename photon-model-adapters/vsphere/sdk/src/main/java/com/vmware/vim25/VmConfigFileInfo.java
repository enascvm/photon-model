
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmConfigFileInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmConfigFileInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}FileInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="configVersion" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="encryption" type="{urn:vim25}VmConfigFileEncryptionInfo" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmConfigFileInfo", propOrder = {
    "configVersion",
    "encryption"
})
@XmlSeeAlso({
    TemplateConfigFileInfo.class
})
public class VmConfigFileInfo
    extends FileInfo
{

    protected Integer configVersion;
    protected VmConfigFileEncryptionInfo encryption;

    /**
     * Gets the value of the configVersion property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getConfigVersion() {
        return configVersion;
    }

    /**
     * Sets the value of the configVersion property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setConfigVersion(Integer value) {
        this.configVersion = value;
    }

    /**
     * Gets the value of the encryption property.
     * 
     * @return
     *     possible object is
     *     {@link VmConfigFileEncryptionInfo }
     *     
     */
    public VmConfigFileEncryptionInfo getEncryption() {
        return encryption;
    }

    /**
     * Sets the value of the encryption property.
     * 
     * @param value
     *     allowed object is
     *     {@link VmConfigFileEncryptionInfo }
     *     
     */
    public void setEncryption(VmConfigFileEncryptionInfo value) {
        this.encryption = value;
    }

}
