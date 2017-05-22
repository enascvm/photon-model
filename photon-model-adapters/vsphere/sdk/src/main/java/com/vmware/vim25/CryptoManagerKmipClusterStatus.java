
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for CryptoManagerKmipClusterStatus complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="CryptoManagerKmipClusterStatus"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="clusterId" type="{urn:vim25}KeyProviderId"/&gt;
 *         &lt;element name="servers" type="{urn:vim25}CryptoManagerKmipServerStatus" maxOccurs="unbounded"/&gt;
 *         &lt;element name="clientCertInfo" type="{urn:vim25}CryptoManagerKmipCertificateInfo" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CryptoManagerKmipClusterStatus", propOrder = {
    "clusterId",
    "servers",
    "clientCertInfo"
})
public class CryptoManagerKmipClusterStatus
    extends DynamicData
{

    @XmlElement(required = true)
    protected KeyProviderId clusterId;
    @XmlElement(required = true)
    protected List<CryptoManagerKmipServerStatus> servers;
    protected CryptoManagerKmipCertificateInfo clientCertInfo;

    /**
     * Gets the value of the clusterId property.
     * 
     * @return
     *     possible object is
     *     {@link KeyProviderId }
     *     
     */
    public KeyProviderId getClusterId() {
        return clusterId;
    }

    /**
     * Sets the value of the clusterId property.
     * 
     * @param value
     *     allowed object is
     *     {@link KeyProviderId }
     *     
     */
    public void setClusterId(KeyProviderId value) {
        this.clusterId = value;
    }

    /**
     * Gets the value of the servers property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the servers property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getServers().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link CryptoManagerKmipServerStatus }
     * 
     * 
     */
    public List<CryptoManagerKmipServerStatus> getServers() {
        if (servers == null) {
            servers = new ArrayList<CryptoManagerKmipServerStatus>();
        }
        return this.servers;
    }

    /**
     * Gets the value of the clientCertInfo property.
     * 
     * @return
     *     possible object is
     *     {@link CryptoManagerKmipCertificateInfo }
     *     
     */
    public CryptoManagerKmipCertificateInfo getClientCertInfo() {
        return clientCertInfo;
    }

    /**
     * Sets the value of the clientCertInfo property.
     * 
     * @param value
     *     allowed object is
     *     {@link CryptoManagerKmipCertificateInfo }
     *     
     */
    public void setClientCertInfo(CryptoManagerKmipCertificateInfo value) {
        this.clientCertInfo = value;
    }

}
