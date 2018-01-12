#Install Jenkins

```bash
kubectl apply -f namespaces.yaml
PASSWORD=`openssl rand -base64 15`; echo "Your password is $PASSWORD"; sed -i.bak s#CHANGE_ME#$PASSWORD# jenkins/k8s/options
kubectl create secret generic jenkins --from-file=jenkins/k8s/options --namespace=jenkins
kubectl create secret generic jenkins-ssh --from-file=<ssh_key_file> --namespace=jenkins
kubectl apply -f jenkins/
kubectl apply -f jenkins/k8s/lb/
```

#Install MYSQL server

```bash
kubectl create secret generic mysql.root --from-file=./mysql/root_password --namespace=jenkins
PASSWORD=`openssl rand -base64 15`; echo "Your password is $PASSWORD"; sed -i.bak s#CHANGE_ME#$PASSWORD# mysql/root_password
kubectl apply -f mysql/
```