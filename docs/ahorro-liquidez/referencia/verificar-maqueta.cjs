// Exercise the prototype with a tiny DOM stub. Never opens a socket or browser.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(path.join(__dirname,'../maqueta.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
assert(html.includes("connect-src 'none'"));assert(!/\bfetch\s*\(/.test(script));
const nodes=new Map();
function node(id){if(!nodes.has(id))nodes.set(id,{textContent:'',innerHTML:'',classList:{add(){},remove(){},toggle(){return false}},setAttribute(){},showModal(){},close(){}});return nodes.get(id)}
const context=vm.createContext({structuredClone,Intl,setTimeout:()=>0,clearTimeout(){},FormData:class{constructor(target){this.fields=target.fields}get(name){return this.fields[name]}},document:{getElementById:node,querySelectorAll:()=>[],body:node('body')}});
vm.runInContext(script,context);
let checks=2;
const equal=(expression,expected)=>{assert.equal(vm.runInContext(expression,context),expected);checks++};
const run=s=>vm.runInContext(s,context);
function submit(fields){node('editForm').onsubmit({preventDefault(){},target:{fields}})}
equal('amounts().free',105000);equal('amounts().reserve',60000);equal('amounts().unassigned',45000);
equal('state.alerts.length',1);run('render();render()');equal('state.alerts.length',1);
run('reserve(1)');submit({account:'1',amount:'100'});
equal('amounts().cash',180000);equal('amounts().reserve',70000);equal('amounts().free',105000);equal('amounts().unassigned',35000);
run('reserve(1)');submit({account:'1',amount:'1000'});equal('amounts().reserve',70000);assert(node('formError').textContent);checks++;
run('reserve(1,true)');submit({account:'1',amount:'100'});equal('amounts().reserve',60000);equal('amounts().cash',180000);
run('expense(1)');submit({account:'credit',amount:'200'});equal('state.debt',65000);equal('amounts().fixed',10000);equal('amounts().free',105000);
run('state=structuredClone(seed);render();expense(1)');submit({account:'1',amount:'200'});equal('amounts().cash',160000);equal('amounts().fixed',10000);equal('amounts().free',105000);
run('state=structuredClone(seed);render();expense()');submit({account:'1',amount:'25',category:'Transporte'});equal('state.alerts.length',2);equal('amounts().free',102500);
run('editGoal()');submit({name:'Meta de prueba',target:'200',date:'2027-01-31'});equal('state.goals.length',3);
run('editGoal(state.goals[2].id)');submit({name:'Meta editada',target:'300',date:''});equal('state.goals[2].name','Meta editada');
run("action('archive',state.goals[2].id)");equal('state.goals.length',2);
run("action('archive',1)");equal('state.goals.length',2);
console.log(checks+' comprobaciones de maqueta correctas; sin navegador, red ni datos reales.');
