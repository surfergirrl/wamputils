package net.iqaros;

import iqaros.pub.demarcation.QueryOuterClass.Query;
import net.iqaros.DemarcationUtil;

import static org.junit.Assert.fail;

import org.junit.Assert;
import org.junit.Test;
import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;
import argo.saj.InvalidSyntaxException;

import com.comhem.lan.utils.argo.ArgoUtils;

public class DemarcationUtilTest {
	
	@Test
	public void customerBuilderTest() {
		String customer = "12345";
		Query.Builder builder = DemarcationUtil.getDemarcationBuilder();
        //Builder builder = builder.getInclusionsBuilder().addCustomerId(customer);
        String serialized =  DemarcationUtil.serializeDemarcation(builder.build());
        
        System.out.println("customerBuilderTest: customer_id=" + customer + "=>" + serialized);
	}
	
	@Test
	public void inclusionSerializationTest() {
		//String encodedProtoString = "v2.GheqBAhMRC1DT1MtMaoECUxJRC1DT1MtMQ";
		Query.Builder builder = DemarcationUtil.getDemarcationBuilder();
		try {		
			builder.setVersion("2.0");
	        builder.getInclusionsBuilder().addCmts("LD-COS-1");
	        builder.getInclusionsBuilder().addCmts("LID-COS-1");
	        String serialized = DemarcationUtil.serializeDemarcation(builder.build());
	        System.out.println("inclusionSerializationTest: " + builder.build() + "=>" + serialized);
			org.junit.Assert.assertTrue(serialized != null);
			//String proto = "v2.CgMyLjAaF6oECExELUNPUy0xqgQJTElELUNPUy0x=>v2.CgMyLjAaF6oECExELUNPUy0xqgQJTElELUNPUy0x";
			//Assert.assertTrue(proto.equalsIgnoreCase(serialized));

		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		
	}

	@Test
	public void validJsonTest() {
		String INCLUSIONS = "{\"inclusions\":[{\"cmts\":\"LD-COS-1\"},{\"cmts\":\"LID-COS-1\"}]}";
		JsonNode validJson;
		try {
			validJson = ArgoUtils.parse(INCLUSIONS);
			System.out.println("expectedResult=" + ArgoUtils.prettyFormat((JsonRootNode) validJson));
		} catch (InvalidSyntaxException e1) {
			//invalid json
			e1.printStackTrace();
			fail();
		}
	}
	
	@Test
	public void deSerializationTest() {
		//String encodedProtoString = "v2.GheqBAhMRC1DT1MtMaoECUxJRC1DT1MtMQ";
		String encodedProtoString="v2.CgMyLjAaF6oECExELUNPUy0xqgQJTElELUNPUy0x";
		Query result = null;
		try {
			result = DemarcationUtil.deserializeDemarcation(encodedProtoString);
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
		System.out.println("deSerializationTest: " + encodedProtoString + "=>" + result);
		org.junit.Assert.assertTrue(result != null);
	}
	
	public static void main(String[] args) {
		String str = DemarcationUtil.getDemarcationFromCustomer("123456");
		System.out.println("demarcation=" + str);
	}
}

