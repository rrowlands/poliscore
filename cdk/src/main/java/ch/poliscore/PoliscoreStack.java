package ch.poliscore;

import software.constructs.Construct;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.Runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PoliscoreStack extends Stack {
    public PoliscoreStack(final Construct parent, final String name) {
        super(parent, name);

        Table dynamodbTable = new Table(this, "poliscore", TableProps.builder()
                .tableName("poliscore")
                .partitionKey(Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build());

        

        
        Map<String, String> lambdaEnvMap = new HashMap<>();
        lambdaEnvMap.put("TABLE_NAME", dynamodbTable.getTableName());
        lambdaEnvMap.put("PRIMARY_KEY","id");

        Function getOneItemFunction = new Function(this, "bill-processor",
        		FunctionProps.builder()
                .code(Code.fromAsset("../bill-processor/target/bill-processor-0.0.1-SNAPSHOT.jar"))
                .handler("ch.poliscore.LambdaHandler")
                .runtime(Runtime.JAVA_11)
                .environment(lambdaEnvMap)
                .timeout(Duration.seconds(30))
                .memorySize(512)
                .build());

        dynamodbTable.grantReadWriteData(getOneItemFunction);
        
        
        

//        RestApi api = new RestApi(this, "itemsApi",
//                RestApiProps.builder().restApiName("Items Service").build());
//
//        IResource items = api.getRoot().addResource("items");
//
//        Integration getAllIntegration = new LambdaIntegration(getAllItemsFunction);
//        items.addMethod("GET", getAllIntegration);
//
//        Integration createOneIntegration = new LambdaIntegration(createItemFunction);
//        items.addMethod("POST", createOneIntegration);
//        addCorsOptions(items);
//
//
//
//        IResource singleItem = items.addResource("{id}");
//        Integration getOneIntegration = new LambdaIntegration(getOneItemFunction);
//        singleItem.addMethod("GET",getOneIntegration);
//
//        Integration updateOneIntegration = new LambdaIntegration(updateItemFunction);
//        singleItem.addMethod("PATCH",updateOneIntegration);
//
//        Integration deleteOneIntegration = new LambdaIntegration(deleteItemFunction);
//        singleItem.addMethod("DELETE",deleteOneIntegration);
//        addCorsOptions(singleItem);
    }



//    private void addCorsOptions(IResource item) {
//        List<MethodResponse> methoedResponses = new ArrayList<>();
//
//        Map<String, Boolean> responseParameters = new HashMap<>();
//        responseParameters.put("method.response.header.Access-Control-Allow-Headers", Boolean.TRUE);
//        responseParameters.put("method.response.header.Access-Control-Allow-Methods", Boolean.TRUE);
//        responseParameters.put("method.response.header.Access-Control-Allow-Credentials", Boolean.TRUE);
//        responseParameters.put("method.response.header.Access-Control-Allow-Origin", Boolean.TRUE);
//        methoedResponses.add(MethodResponse.builder()
//                .responseParameters(responseParameters)
//                .statusCode("200")
//                .build());
//        MethodOptions methodOptions = MethodOptions.builder()
//                .methodResponses(methoedResponses)
//                .build()
//                ;
//
//        Map<String, String> requestTemplate = new HashMap<>();
//        requestTemplate.put("application/json","{\"statusCode\": 200}");
//        List<IntegrationResponse> integrationResponses = new ArrayList<>();
//
//        Map<String, String> integrationResponseParameters = new HashMap<>();
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Headers","'Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,X-Amz-User-Agent'");
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Origin","'*'");
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Credentials","'false'");
//        integrationResponseParameters.put("method.response.header.Access-Control-Allow-Methods","'OPTIONS,GET,PUT,POST,DELETE'");
//        integrationResponses.add(IntegrationResponse.builder()
//                .responseParameters(integrationResponseParameters)
//                .statusCode("200")
//                .build());
//        Integration methodIntegration = MockIntegration.Builder.create()
//                .integrationResponses(integrationResponses)
//                .passthroughBehavior(PassthroughBehavior.NEVER)
//                .requestTemplates(requestTemplate)
//                .build();
//
//        item.addMethod("OPTIONS", methodIntegration, methodOptions);
//    }
}
