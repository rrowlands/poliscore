package us.poliscore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.FunctionProps;
import software.amazon.awscdk.services.lambda.FunctionUrl;
import software.amazon.awscdk.services.lambda.FunctionUrlAuthType;
import software.amazon.awscdk.services.lambda.FunctionUrlCorsOptions;
import software.amazon.awscdk.services.lambda.HttpMethod;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.constructs.Construct;

class PoliscoreStack extends Stack {
    public PoliscoreStack(final Construct parent, final String name, final StackProps props) {
        super(parent, name, props);

        Table table = new Table(this, "poliscore", TableProps.builder()
                .tableName("poliscore")
                .partitionKey(Attribute.builder()
                        .name("id")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("page")
                        .type(AttributeType.STRING)
                        .build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .timeToLiveAttribute("expireDate")
                .build());
        
        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("ObjectsByLocation")
                .partitionKey(Attribute.builder()
                        .name("idClassPrefix")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("location")
                        .type(AttributeType.STRING)
                        .build())
                .build());
        
        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("ObjectsByDate")
                .partitionKey(Attribute.builder()
                        .name("idClassPrefix")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("date")
                        .type(AttributeType.STRING)
                        .build())
                .build());
        
        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("ObjectsByRating")
                .partitionKey(Attribute.builder()
                        .name("idClassPrefix")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("rating")
                        .type(AttributeType.NUMBER)
                        .build())
                .build());
        

        
        Map<String, String> lambdaEnvMap = new HashMap<>();
        lambdaEnvMap.put("TABLE_NAME", table.getTableName());
        lambdaEnvMap.put("PRIMARY_KEY","id");

        Function fPoliscore = new Function(this, "bill-processor",
        		FunctionProps.builder()
                .code(Code.fromAsset("../webapp/target/function.zip"))
                .handler("not.used.in.provided.runtimei")
                .runtime(Runtime.PROVIDED_AL2023)
                .environment(lambdaEnvMap)
                .timeout(Duration.minutes(15))
                .architecture(Architecture.ARM_64) // Required if you're building on MacOSX M* ARM chipset)
                .memorySize(128)
                .environment(Map.of("DISABLE_SIGNAL_HANDLERS", "true"))
                .build());
        
        FunctionUrl.Builder.create(this, "poliscore-bill-processor-url")
        	.function(fPoliscore)
        	.authType(FunctionUrlAuthType.NONE)
        	.cors(FunctionUrlCorsOptions.builder().allowedOrigins(Arrays.asList("*")).allowedMethods(Arrays.asList(HttpMethod.ALL)).build())
        	.build();

        table.grantReadWriteData(fPoliscore);
        
        Secret dbReadSecret2 = new Secret(this, "ipgeolocation");
        dbReadSecret2.grantRead(fPoliscore.getRole());
        
        
        
        
//        new Bucket(this, "PoliscoreProdBucket",
//        		BucketProps.builder()
//        		.bucketName("poliscore-prod")
//        		.publicReadAccess(false)
//        		.removalPolicy(RemovalPolicy.RETAIN_ON_UPDATE_OR_DELETE).build());
        
        
        
        
        
        
        
        
        

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
